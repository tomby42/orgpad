(ns ^{:doc "Default root read/write parser"}
  orgpad.parsers.default-root
  (:require [clojure.set :as set]
            [com.rpl.specter :refer [keypath]]
            [datascript.transit :as dt]
            [orgpad.core.store :as store]
            [orgpad.core.orgpad :as orgpad]
            [orgpad.parsers.default-unit :as dp :refer [read mutate updated?]]
            [orgpad.tools.colls :as colls]
            [orgpad.tools.dscript :as ds]
            [orgpad.tools.orgpad :as ot]
            [orgpad.tools.geocache :as geocache]
            [orgpad.tools.jcolls :as jscolls]
            [orgpad.tools.dom :as dom]
            [orgpad.components.registry :as registry]
            [orgpad.net.com :as net]
            [orgpad.effects.net :as enet]))

(defn- find-root-view-info
  [db]
  (let [root-unit (store/query db [:entity 0])]
    (ds/find-props root-unit (fn [u] (= (u :orgpad/type) :orgpad/root-unit-view)))))

(defn- get-view-stack
  [db]
  (let [stack (-> db (store/query [:app-state :orgpad/view-stack]) first)]
    (if stack
      stack
      [])))

(defn- set-view-stack
  [db view-stack]
  (if (empty? view-stack)
    (store/transact db [[:app-state :orgpad/view-stack] nil])
    (store/transact db [[:app-state :orgpad/view-stack] view-stack])))

(defmethod read :orgpad/root-view
  [{ :keys [state query] :as env } k params]
  (let [db state

        root-view-info
        (find-root-view-info db)

        root-info
        (registry/get-component-info :orgpad/root-view)

        view-stack (get-view-stack db)

        [_ cr-id v-name v-type v-path]
        (if view-stack
          (last view-stack)
          [nil nil nil nil nil])

        view-name
        (or v-name
            (-> root-info :orgpad/default-view-info :orgpad/view-name))

        view-type
        (or v-type
            (-> root-info :orgpad/default-view-info :orgpad/view-type))

        view-path
        (or v-path [])

        current-root-id
        (or cr-id (-> root-view-info :orgpad/refs last :db/id))]

;;    (println "root parser" current-root-id view-name view-type view-path)

    (query (merge env { :view-name view-name
                        :unit-id current-root-id
                        :view-type view-type
                        :view-path view-path })
           :orgpad/unit-view params) ))

(defmethod updated? :orgpad/root-view
  [node { :keys [state] } _]
  (let [value (aget node "value")
        root-view-info (find-root-view-info state)
        old-root (ot/uid value)
        current-root (-> root-view-info :orgpad/refs last :db/id)]
    (or (not-empty (get-view-stack state))
        (not= current-root old-root))))

(defmethod read :orgpad/app-state
  [{ :keys [state] :as env } _ _]
  (-> state (store/query [:app-state]) first))

(defmethod mutate :orgpad/app-state
  [{:keys [state]} _ path-val]
  {:state (store/transact state [(->> path-val first (into [:app-state])) (second path-val)])})

(defmethod updated? :orgpad/app-state
  [_ { :keys [state] } _]
  (store/changed? state [:app-state]))

(defmethod mutate :orgpad/root-view-stack
  [{ :keys [state parser-state-push!] } _ { :keys [db/id orgpad/view-name orgpad/view-type orgpad/view-path] }]
  (let [view-stack (get-view-stack state)
        pos (or (and view-stack (count view-stack)) 0)]
    (parser-state-push! :orgpad/root-view [])
    { :state (set-view-stack state (conj view-stack [pos id view-name view-type view-path])) }))

(defn update-parser-state!
  [db old new]
  (let [root (aget old "children" 0)
        child (aget new "children" 0)
        uid (ot/uid (aget child "value"))
        idx (.findIndex (aget root "children")
                        (fn [node] (= (-> node (aget "value") ot/uid)
                                      uid)))
        unit (-> child (aget "value") :unit)
        props (-> db (store/query[:entity (:db/id unit)])
                  ds/props->maps)
        unit' (assoc unit :orgpad/props-refs props)
        new-value (update-in (aget root "value")
                             [:unit :orgpad/refs]
                             update idx
                             (fn [unit-tree]
                               (let [v (:view unit-tree)
                                     view (or
                                           (-> {:orgpad/props-refs props}
                                               (ds/find-props-all
                                                (partial ot/props-pred-no-ctx (:orgpad/view-name v)
                                                         (:orgpad/view-type v) (or (:orgpad/type v)
                                                                                   :orgpad/unit-view)))
                                               first)
                                           v)]
                                 (assoc unit-tree
                                        :unit unit'
                                        :props props
                                        :view view))))]
    ;; (js/console.log "update-parser-state!" idx child)
    ;; (js/console.log "unit'" unit')
    ;; (js/console.log "props" props)
    ;; (js/console.log "new-value" new-value)
    ;; (js/console.log "root" root)
    (aset child "value" (assoc (aget child "value") :unit unit' :props props))
    (aset root "children" idx child)
    (aset root "value" new-value)
    (aset old "value" new-value)))

(defn- sequent?
  [db stack]
  (let [uid (-> stack last second)
        parent (if (= (count stack) 1) 0 (-> stack pop last second))]
    (not-empty (store/query db '[:find ?x
                                 :in $ ?p ?u
                                 :where
                                 [?p :orgpad/refs ?x]
                                 [(= ?x ?u)]] [parent uid]))))

(defmethod mutate :orgpad/root-unit-close
  [{ :keys [state parser-state-pop!] } _ params]
  (let [view-stack (get-view-stack state)]
    (parser-state-pop! :orgpad/root-view []
                       (if (sequent? state view-stack)
                         (partial update-parser-state! state)
                         nil))
    { :state (set-view-stack state (pop view-stack)) }))

(defmethod mutate :orgpad/root-view-conf
  [{ :keys [state force-update!] } _ [{:keys [unit view path-info] } {:keys [attr value]}]]
  (let [path-info-id (path-info :db/id)]
    (force-update!)
    { :state
      (if path-info-id
        (store/transact state [[:db/add path-info-id attr value]])
        (store/transact state [(merge path-info { :db/id -1
                                                  :orgpad/refs (unit :db/id)
                                                  :orgpad/type :orgpad/unit-path-info
                                                  attr value })
                               [:db/add (unit :db/id) :orgpad/props-refs -1]
                               ]))
     }))

(defmethod mutate :orgpad/root-new-view
  [env _ [unit-tree attr]]
  (-> env
      (mutate :orgpad.units/clone-view [unit-tree (attr :value)])
      (->> (merge env))
      (mutate :orgpad/root-view-conf [unit-tree attr])))

(defmethod mutate :orgpad/export-as-html
  [{ :keys [state] } _ storage-el]
  { :state state
    :effect #(orgpad/export-html-by-uri state storage-el) })

(defmethod mutate :orgpad/save-orgpad
  [{ :keys [state] } _ _]
  { :state state
    :effect #(orgpad/save-file-by-uri state) })

(defmethod mutate :orgpad/load-orgpad
  [{:keys [state force-update! transact!] } _ files]
  {:state (store/transact state [[:app-state :loading] true])
   :effect #(try
              (transact! [[:orgpad/loaded (orgpad/load-orgpad state files)]])
              (catch :default e
                (js/console.log "error loading" e)
                ;; TODO - show error message
                ))})

(defmethod mutate :orgpad/import-orgpad
  [{ :keys [state force-update! transact!] } _ files]
  {:state (store/transact state [[:app-state :loading] true])
   :effect #(try
              (transact! [[:orgpad/loaded (orgpad/import-orgpad state files)]])
              (catch :default e
                (js/console.log "error importing" e)
                ;; TODO - show error message
                ))})

(defn- reset-n-rebuild
  [{:keys [force-update! global-cache]} new-state]
  (jscolls/clear! global-cache)
  (geocache/rebuild! global-cache new-state)
  (force-update!))

(defmethod mutate :orgpad/loaded
  [env _ new-state]
  (reset-n-rebuild env new-state)
  (when-let [name (-> new-state (store/query [:app-state]) first :orgpad-name)]
    (dom/set-el-text (dom/ffind-tag :title) name))
  { :state new-state })

(defmethod mutate :orgpad/download-orgpad-from-url
  [{ :keys [state transact!] } _ url]
  { :state (store/transact state [[:app-state :loading] true])
    :effect #(orgpad/download-orgpad-from-url url transact!) })

(defmethod read :orgpad/undoable?
  [{ :keys [state] } _ _]
  (store/undoable? state))

(defmethod read :orgpad/redoable?
  [{ :keys [state] } _ _]
  (store/redoable? state))

;; TODO - if unit that we are editing do not exist pop the stack unit it exist
;; (defn- resolve-parser-state!
;;   [{:keys [state parser-state-pop! parser-state-push!]} new-state]
;;   (let [view-stack (get-view-stack state)
;;         new-view-stack (get-view-stack new-state)]
;;     (case (compare (count view-stack) (count new-view-stack))
;;       1 (parser-state-pop! :orgpad/root-view []
;;                            (if (sequent? state view-stack)
;;                              (partial update-parser-state! state)
;;                              nil))
;;       -1 (parser-state-push! :orgpad/root-view [])
;;       nil)))

(defn- not-exists?
  [state uid]
  (->> (store/query state [:entity uid]) (into {}) empty?))

(defn- descendant?
  [new-state old-state pid did]
  (let [u (if (not-exists? new-state did)
            (-> old-state (store/query [:entity did]) ds/entity->map)
            (-> new-state (store/query [:entity did]) ds/entity->map))
        uid  (if (or (= (:orgpad/type u) :orgpad/unit)
                     (= (:orgpad/type u) :orgpad/root-unit))
               (:db/id u)
               (-> u :orgpad/refs first :db/id))]
    (if (not-exists? new-state uid)
      (ot/is-descendant? old-state pid uid)
      (ot/is-descendant? new-state pid uid))))

(defn resolve-parser-state!
  [{:keys [state parser-state-pop! force-update!]} new-state]
  (let [old-view-stack (get-view-stack state)
        es (:datom (store/changed-entities new-state))]
    (reduce (fn [state' [_ cr-id _ _ _]]
              (if (or (not-exists? state' cr-id)
                      (not-every? (partial descendant? state' state cr-id) es))
                (let [view-stack (get-view-stack state')]
                  (parser-state-pop! :orgpad/root-view []
                                     (if (sequent? state view-stack)
                                       (partial update-parser-state! state)
                                       nil))
                  (set-view-stack state' (pop view-stack)))
                (reduced state')))
              new-state old-view-stack)))

;; update if view-stack updated
(defmethod mutate :orgpad/undo
  [{ :keys [state global-cache] :as env } _ _]
  (let [new-state (->> state
                       store/undo
                       (resolve-parser-state! env))]
    (geocache/update-changed-units! global-cache state new-state (:datom (store/changed-entities new-state)))
    { :state new-state }))

(defmethod mutate :orgpad/redo
  [{ :keys [state global-cache] :as env } _ _]
  (let [new-state (->> state
                       store/redo
                       (resolve-parser-state! env))]
    (geocache/update-changed-units! global-cache state new-state (:datom (store/changed-entities new-state)))
    { :state new-state }))

(defmethod read :orgpad/history-info
  [{ :keys [state] } _ _]
  (store/history-info state))

(defmethod mutate :orgpad/history-change
  [{ :keys [state] :as env } _ { :keys [old-finger new-finger] }]
  { :state (reduce (fn [state _]
                     (if (< old-finger new-finger)
                       (:state (mutate env :orgpad/redo []))
                       (:state (mutate env :orgpad/undo []))))
                   state
                   (range old-finger new-finger (if (< old-finger new-finger) 1 -1))) })

(defmethod mutate :orgpad.units/select
  [{:keys [state]} _ {:keys [pid uid]}]
  {:state (store/transact state [[:app-state :selections (keypath pid)] #{uid}])})

(defmethod mutate :orgpad.units/deselect-all
  [{:keys [state]} _ {:keys [pid]}]
  {:state (store/transact state [[:app-state :selections (keypath pid)] nil])})

(defmethod mutate :orgpad.units/select-by-pattern
  [{:keys [state]} _ {:keys [params unit-tree]}]
  (let [pid (ot/uid unit-tree)
        selected-units (ot/search-child-by-descendant-txt-pattern state pid
                                                                  (:selection-text params))
        cnt (count selected-units)]
    (println "selected: " pid selected-units (set (map first selected-units)))
    {:state (store/transact state [[:app-state :selections (keypath pid)] (set (map first selected-units))])
     :response (str "Selected " cnt
                    (if (< cnt 2) " unit." " units."))}))

(defmethod mutate :orgpad.units/copy
  [{:keys [state]} _ {:keys [pid selection]}]
  (let [data (ot/copy-descendants-from-db state pid [] selection)]
    ;; (js/console.log "copy " data)
    {:state (store/transact state [[:app-state :clipboards (keypath pid)] data])}))

(defmethod read :orgpad/styles
  [{:keys [state]} _ {:keys [view-type]}]
  (store/query state '[:find [(pull ?e [*]) ...]
                       :in $ ?view-type
                       :where
                       [?e :orgpad/view-type ?view-type]]
               [view-type]))

(defmethod read :orgpad/style
  [{:keys [state]} _ {:keys [view-type style-name]}]
  (ot/get-style-from-db state view-type style-name))

(defmethod mutate :orgpad.style/update
  [{:keys [state]} _ {:keys [style prop-name prop-val]}]
  ;; (js/console.log "orgpad.style/update" style prop-name prop-val)
  {:state (store/transact state [[:db/add (:db/id style) prop-name prop-val]])})

(defn- get-style-default
  [type]
  (-> (registry/get-registry)
      vals
      (->> (drop-while #(nil? (get-in % [:orgpad/child-props-default type]))))
      first
      (get-in [:orgpad/child-props-default type])))

(defmethod mutate :orgpad.style/new
  [{:keys [state]} _ {:keys [type name based-on]}]
  (let [style (-> (ot/get-style-from-db state type based-on) 
                  (assoc :orgpad/style-name name :db/id -1))
        new-state (store/transact state [style])]
    ;; (js/console.log "orgpad.style/new" type name style)
    {:state new-state}))

(defn- uids-with-props-refs
  [state id]
  (let [find-all '[:find [?u ...] 
                   :in $ ?id
                   :where [?u :orgpad/props-refs ?id]]]
    (store/query state find-all [id])))

(defn- add-props-refs-id-query
  [uids id]
  (map #(vector :db/add % :orgpad/props-refs id) uids))

(defn- change-props-refs-id-query
  [uids old-id new-id]
  (let [remove-query (map #(vector :db/retract % :orgpad/props-refs old-id) uids)  
        add-query (map #(vector :db/add % :orgpad/props-refs new-id) uids)]
    (into remove-query add-query)))

(defn- uids-with-style
  "Get all unit ids having given style."
  [state name type]
  (let [prop-type (ot/prop-type-style->prop-type type)
        find-all '[:find [?p ...] 
                   :in $ ?name ?prop-type
                   :where
                    [?p :orgpad/view-type ?prop-type]
                    [?p :orgpad/view-style ?name]]]
    (store/query state find-all [name prop-type])))

(defn- change-style-name-query
  [uids old-name new-name]
  (let [remove-query (map #(vector :db/retract % :orgpad/view-style old-name) uids)  
        add-query (map #(vector :db/add % :orgpad/view-style new-name) uids)]
    (into remove-query add-query)))

(defmethod mutate :orgpad.style/remove
  [{:keys [state]} _ {:keys [id name type replace-name]}]
  (let [replace-name' (if replace-name replace-name "default")
        replace-id (:db/id (ot/get-style-from-db state type replace-name'))
        name-uids (uids-with-style state name type)
        props-refs-uids (uids-with-props-refs state id)]
    ;(js/console.log "orgpad.style/remove" id name type replace-name' replace-id)
    {:state (store/transact state
              (colls/minto [[:db.fn/retractEntity id]]
                            (change-style-name-query name-uids name replace-name')
                            (add-props-refs-id-query props-refs-uids replace-id)))}))

(defmethod mutate :orgpad.style/rename
  [{:keys [state]} _ {:keys [id old-name new-name type]}]
  (let [uids (uids-with-style state old-name type)
        rename-query [[:db/retract id :orgpad/style-name old-name]
                      [:db/add id :orgpad/style-name new-name]]]
    ;(js/console.log "orgpad.style/rename" id old-name new-name type)
    {:state (store/transact state
              (into rename-query (change-style-name-query uids old-name new-name)))}))

(defmethod mutate :orgpad.style/rebase
  [{:keys [state]} _ {:keys [id name type based-on]}]
  (let [style (-> (ot/get-style-from-db state type based-on) 
                  (assoc :orgpad/style-name name :db/id id))]
    ;(js/console.log "orgpad.style/rebase" id name type based-on style)
    {:state (store/transact state [style])}))

(defmethod read :orgpad/root-view-stack-info
  [{:keys [parser-stack-info]} _ [key params]]
  (parser-stack-info key params))

(defn- comp-changes
  [state net-update-ignore?]
  (if (and (not= net-update-ignore? :all)
           (net/is-online?))
    (let [changes (store/cumulative-changes state)
          _ (js/console.log "changes: " changes)
          atom (-> state (store/query []) first)
          {:keys [datoms mapping new-indices]}
          (ot/datoms-uid->squuid (:datom changes) (:uid->squuid atom))
          new-atom (assoc atom
                          :net-update-ignore? :none
                          :uid->squuid mapping
                          :uid-last-index (or (->> new-indices keys (apply max)) (:uid-last-index atom))
                          :squuid->uid (merge (:squuid->uid atom)
                                              (set/map-invert new-indices)))]
      [[[] new-atom] datoms])
    [[[:net-update-ignore?] :none] nil]))

(defmethod mutate :orgpad/log
  [{:keys [transact! state]} _ old-state]
  (let [net-update-ignore? (-> state (store/query [:net-update-ignore?]) first)
        [new-atom-qry datoms] (comp-changes state net-update-ignore?)]
    (js/console.log "log:" net-update-ignore? new-atom-qry datoms)
    {:state (store/transact state new-atom-qry)
     :effect #(when (and (not= net-update-ignore? :all)
                         (net/is-online?))
                (let [atom (second new-atom-qry)]
                  (js/console.log "log:" state old-state)
                  (enet/update! (:orgpad-uuid atom)
                                (cond-> {:atom nil :db (dt/write-transit-str [])}
                                  (not= net-update-ignore? :global) (assoc :db (dt/write-transit-str datoms))
                                  (not= net-update-ignore? :local) (assoc :atom (select-keys atom [:app-state]))))))}))
