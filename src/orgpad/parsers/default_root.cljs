(ns ^{:doc "Default root read/write parser"}
  orgpad.parsers.default-root
  (:require [com.rpl.specter :refer [keypath]]
            [orgpad.core.store :as store]
            [orgpad.core.orgpad :as orgpad]
            [orgpad.parsers.default-unit :as dp :refer [read mutate updated?]]
            [orgpad.tools.dscript :as ds]
            [orgpad.tools.orgpad :as ot]
            [orgpad.tools.geocache :as geocache]
            [orgpad.tools.jcolls :as jscolls]
            [orgpad.components.registry :as registry]))

(defn- find-root-view-info
  [db]
  (let [root-unit (store/query db [:entity 0])]
    (ds/find-props root-unit (fn [u] (= (u :orgpad/type) :orgpad/root-unit-view)))))

(defmethod read :orgpad/root-view
  [{ :keys [state query] :as env } k params]
  (let [db state

        root-view-info
        (find-root-view-info db)

        root-info
        (registry/get-component-info :orgpad/root-view)

        [_ cr-id v-name v-type v-path]
        (if (root-view-info :orgpad/view-stack)
          (->> root-view-info :orgpad/view-stack sort last)
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
    (not= current-root old-root)))

(defmethod read :orgpad/app-state
  [{ :keys [state] :as env } _ _]
  (-> state (store/query []) first))

(defmethod mutate :orgpad/app-state
  [{:keys [state]} _ path-val]
  { :state (store/transact state path-val) })

(defmethod updated? :orgpad/app-state
  [_ { :keys [state] } _]
  (store/changed? state []))

(defmethod mutate :orgpad/root-view-stack
  [{ :keys [state parser-state-push!] } _ { :keys [db/id orgpad/view-name orgpad/view-type orgpad/view-path] }]
  (let [root-view-info (find-root-view-info state)
        rvi-id (root-view-info :db/id)
        pos (or (-> root-view-info :orgpad/view-stack count) 0)]
    (parser-state-push! :orgpad/root-view [])
    { :state (store/transact state [[:db/add rvi-id :orgpad/view-stack [pos id view-name view-type view-path]]]) }))

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
                             assoc :unit unit' :props props)]
    ;; (js/console.log "update-parser-state!" idx child)
    ;; (js/console.log unit')
    ;; (js/console.log props)
    ;; (js/console.log new-value)
    ;; (js/console.log root)
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
  [{ :keys [state parser-state-pop!] } _ {:keys [db/id orgpad/view-name orgpad/view-type orgpad/view-path] }]
  (let [root-view-info (find-root-view-info state)
        rvi-id (root-view-info :db/id)
        view-stack (->> root-view-info :orgpad/view-stack sort (into []))
        last-view (last view-stack)]
    (parser-state-pop! :orgpad/root-view [] (if (sequent? state view-stack) (partial update-parser-state! state) nil))
    { :state (store/transact state [[:db/retract rvi-id :orgpad/view-stack last-view] ]) }))

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
  [{ :keys [state force-update! transact!] } _ files]
  { :state (store/transact state [[:loading] true])
    :effect #(transact! [[ :orgpad/loaded (orgpad/load-orgpad state files) ]])})

(defmethod mutate :orgpad/loaded
  [{ :keys [force-update! global-cache]} _ new-state]
  (force-update!)
  (jscolls/clear! global-cache)
  (geocache/rebuild! global-cache new-state)
  { :state new-state })

(defmethod mutate :orgpad/download-orgpad-from-url
  [{ :keys [state transact!] } _ url]
  { :state (store/transact state [[:loading] true])
    :effect #(orgpad/download-orgpad-from-url url transact!) })

(defmethod read :orgpad/undoable?
  [{ :keys [state] } _ _]
  (store/undoable? state))

(defmethod read :orgpad/redoable?
  [{ :keys [state] } _ _]
  (store/redoable? state))

(defmethod mutate :orgpad/undo
  [{ :keys [state global-cache] } _ _]
  (let [new-state (store/undo state)]
    (geocache/update-changed-units! global-cache state new-state (:datom (store/changed-entities new-state)))
    { :state new-state }))

(defmethod mutate :orgpad/redo
  [{ :keys [state global-cache] } _ _]
  (let [new-state (store/redo state)]
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
  {:state (store/transact state [[:selections (keypath pid)] #{uid}])})

(defmethod mutate :orgpad.units/deselect-all
  [{:keys [state]} _ {:keys [pid]}]
  {:state (store/transact state [[:selections (keypath pid)] nil])})

(defmethod mutate :orgpad.units/select-by-pattern
  [{:keys [state]} _ {:keys [params unit-tree]}]
  (let [pid (ot/uid unit-tree)
        selected-units (ot/search-child-by-descendant-txt-pattern state pid
                                                                  (:selection-text params))
        cnt (count selected-units)]
    (println "selected: " pid selected-units (set (map first selected-units)))
    {:state (store/transact state [[:selections (keypath pid)] (set (map first selected-units))])
     :response (str "Selected " cnt
                    (if (< cnt 2) " unit." " units."))}))

(defmethod mutate :orgpad.units/copy
  [{:keys [state]} _ {:keys [pid selection]}]
  (let [data (ot/copy-descendants-from-db state pid [] selection)]
    (js/console.log "copy " data)
    {:state (store/transact state [[:clipboards (keypath pid)] data])}))

(defmethod read :orgpad/styles
  [{:keys [state query] :as env} _ {:keys [view-type]}]
  (store/query state '[:find [(pull ?e [*])]
                       :in $ ?view-type
                       :where
                       [?e :orgpad/view-type ?view-type]]
               [view-type]))

(defmethod read :orgpad/style
  [{:keys [state query] :as env} _ {:keys [view-type style-name]}]
  (store/query state '[:find (pull ?e [*]) .
                       :in $ ?view-type ?style-name
                       :where
                       [?e :orgpad/view-type ?view-type]
                       [?e :orgpad/style-name ?style-name]]
               [view-type style-name]))
