(ns ^{:doc "Definition of default unit parser"}
  orgpad.parsers.default-unit
  (:require [orgpad.core.store :as store]
            [orgpad.tools.dscript :as ds]
            [orgpad.tools.colls :as colls]
            [orgpad.tools.orgpad :as ot]
            [orgpad.tools.geocache :as geocache]
            [orgpad.components.registry :as registry]))

;;; Dispatch definitions

(declare read)
(declare mutate)
(declare updated?)

(defn dispatch3
  "Helper function for implementing :read and :mutate as multimethods. Use this
   as the dispatch-fn."
  [_ key _]
  key)

(defn dispatch3key
  "Helper function for implementing :update? as multimethod. Use this
   as the dispatch-fn."
  [node _ _]
  (aget node "key"))

;;; General method declaration

(defmulti read dispatch3)
(defmulti mutate dispatch3)
(defmulti updated? dispatch3key)

;;; default read method

(defn get-view-props
  [unit {:keys [orgpad/view-type orgpad/view-name orgpad/type]}]
  (ds/find-props-all unit (partial ot/props-pred-no-ctx view-name view-type type)))

(defn- get-path-info
  [unit view-path]
  (ds/find-props unit (fn [u]
                        (and (= (u :orgpad/view-path) view-path)
                             (= (u :orgpad/type) :orgpad/unit-path-info)))))

(defn- parse
  [query tree env unit-id view-unit view-info view-path view-contexts params uid old-node]
  (if (and old-node
           (not (or (aget old-node "changed?")
                    (aget old-node "me-changed?")))
           (= uid (-> (aget old-node "value") ot/uid)))
    (do
      ;; (println "skipping" old-node uid)
      (.push @tree old-node)
      (aget old-node "value"))
    (query (merge env
                  {:unit-id    uid
                   :old-node   nil
                   :view-path  (-> view-path (conj unit-id) (conj (view-unit :orgpad/view-name)))
                   :view-name  (-> view-info :orgpad/child-default-view-info :orgpad/view-name)
                   :view-type  (-> view-info :orgpad/child-default-view-info :orgpad/view-type)
                   :view-contexts view-contexts})
           :orgpad/unit-view params)))

(defn- build-unit
  [unit view-unit old-node view-info parser' global-cache]
  (let [eunit (ds/entity->map unit)]
    (if (:orgpad/needs-children-info view-info)
      (if (:orgpad/visible-children-picker view-info)
        (update eunit :orgpad/refs
                (fn [refs]
                  (let [sort-refs (if (:orgpad/refs-order eunit)
                                    (map second (:orgpad/refs-order eunit))
                                    [])] ;; TODO: hack for links that are units too - need to get all siblings of current unit
                    (mapv (fn [[u o]] (parser' u o))
                          ((:orgpad/visible-children-picker view-info) unit view-unit
                                                                       (if (and old-node (= (aget old-node "key") :orgpad/unit-view)) old-node nil)
                                                                       global-cache sort-refs)))))
        (let [old-children-nodes (and old-node (aget old-node "children"))
              use-children-nodes? (and old-node
                                       (= (aget old-node "key") :orgpad/unit-view)
                                       (= (alength old-children-nodes) (count (unit :orgpad/refs))))]
          (update eunit :orgpad/refs
                  (fn [refs]
                    (let [refs' (map :db/id refs)]
                      ;; (println (eunit :orgpad/refs-order))
                      (if (eunit :orgpad/refs-order)
                        (let [children (if use-children-nodes?
                                         (into {} (map (juxt identity parser')
                                                       refs' old-children-nodes))
                                         (into {} (map (juxt identity parser') refs')))]
                          (mapv (comp children second) (eunit :orgpad/refs-order)))
                        (if use-children-nodes?
                          (into [] (map parser' refs' old-children-nodes))
                          (mapv parser' refs'))))))))
      (if (eunit :orgpad/refs-order)
        (update eunit :orgpad/refs
                (fn [refs]
                  (let [children (into {} (map (juxt :db/id identity) refs))]
                    (mapv (comp children second) (eunit :orgpad/refs-order)))))
        eunit))))

(defmethod read :orgpad/unit-view
  [{:keys [state query old-node tree unit-id view-name view-type view-path view-contexts global-cache] :as env} k params]
;;  (println "read :orgpad/unit-view" unit-id view-name view-type view-path view-contexts k)

  (let [db  state

        unit
        (store/query db [:entity unit-id])

        path-info
        (get-path-info unit view-path)

        path-info'
        (assoc (or path-info {:orgpad/view-name view-name
                              :orgpad/view-type view-type
                              :orgpad/view-path view-path})
               :orgpad/type :orgpad/unit-view)

        view-info
        (registry/get-component-info (path-info' :orgpad/view-type))

        view-unit-local
        (-> unit (get-view-props path-info') first)

        view-unit
        (or view-unit-local (-> view-info :orgpad/default-view-info (assoc :orgpad/refs [{:db/id unit-id}])))

        props-info
        (when (:orgpad/child-props-types view-info)
          (into [] (map (fn [type]
                          {:orgpad/view-type type
                           :orgpad/view-name (:orgpad/view-name path-info')
                           :orgpad/type      :orgpad/unit-view-child}))
                (:orgpad/child-props-types view-info)))

        view-contexts'
        (if (:orgpad/propagate-props-from-children? view-info)
          (let [view-contexts'' (mapv #(assoc % :orgpad/type :orgpad/unit-view-child-propagated)
                                      view-contexts)]
            (if props-info
              (into view-contexts'' props-info)
              view-contexts''))
          (when props-info
            props-info))

        parser' (partial parse query tree env unit-id view-unit view-info view-path view-contexts' params)

        unit' (build-unit unit view-unit old-node view-info parser' global-cache)

        props
        (when view-contexts
          (into [] (mapcat #(get-view-props unit %)) view-contexts))]

    ;; (js/console.log {:unit unit'
    ;;                  :path-info path-info'
    ;;                  :view view-unit
    ;;                  :props props }
    ;;                 db)

    {:unit unit'
     :path-info path-info'
     :view view-unit
     :props props}))

;;; Default updated? definition

(defmethod updated? :default
  [_ _ _]
  false)

(defmethod updated? :orgpad/unit-view
  [node {:keys [state changed-entities]} force-update-part]

  (let [value (aget node "value")
        changed-datom-entities (aget changed-entities "datom")
        unit (:unit value)]
    (or (nil? unit)
        (force-update-part (:db/id unit))
        (aget changed-datom-entities (:db/id unit))
        (some #(aget changed-datom-entities (:db/id %)) (:orgpad/props-refs unit)))))

;;; Clone of unit view

(defn- clone-view
  [{:keys [unit view]} new-view-name indexer]
  [(merge view {:db/id (vswap! indexer dec)
                :orgpad/type :orgpad/unit-view
                :orgpad/refs (unit :db/id)
                :orgpad/view-name new-view-name})
   [:db/add (unit :db/id) :orgpad/props-refs @indexer]])

(defn- clone-props
  [state uid view-name view-type type indexer new-view-name]
  (let [props (ot/get-child-props-from-db state uid [[:orgpad/view-type view-type]
                                                     [:orgpad/type type]
                                                     [:orgpad/view-name view-name]])
        xform
        (mapcat (fn [[id prop]]
                  [(merge prop {:db/id (vswap! indexer dec)
                                :orgpad/refs (mapv :db/id (:orgpad/refs prop))
                                :orgpad/view-name new-view-name})
                   [:db/add id :orgpad/props-refs @indexer]]))]
    (into [] xform props)))

(defn- clone-child-props
  [state info {:keys [unit view]} new-view-name indexer]
  (if (info :orgpad/needs-children-info)
    (into []
          (mapcat
           (fn [type]
             (clone-props state (:db/id unit) (:orgpad/view-name view) type
                          :orgpad/unit-view-child indexer new-view-name)))
          (info :orgpad/child-props-types))
    []))

(defn- clone-propagated-child-props
  [state info {:keys [unit view]} new-view-name indexer]
  (if (info :orgpad/needs-children-info)
    (let [unit' (-> state (store/query [:entity (:db/id unit)]) ds/entity->map)]
      (into []
            (mapcat (fn [u]
                      (into []
                            (mapcat
                             (fn [type]
                               (clone-props state (:db/id u) (:orgpad/view-name view) type
                                            :orgpad/unit-view-child-propagated indexer new-view-name)))
                            (info :orgpad/child-props-types))))
            (:orgpad/refs unit')))
    []))

(defmethod mutate :orgpad.units/clone-view
  [{:keys [state global-cache]} _ [unit-tree new-view-name]]
  (let [indexer (volatile! 0)
        info (registry/get-component-info (-> unit-tree :view :orgpad/view-type))
        cloned-view (clone-view unit-tree new-view-name indexer)
        cloned-child-props (clone-child-props state info unit-tree new-view-name indexer)
        cloned-propagated-child-props (clone-propagated-child-props state info unit-tree
                                                                    new-view-name indexer)]
    (geocache/copy global-cache (ot/uid unit-tree) (-> unit-tree :view :orgpad/view-name) new-view-name)
    {:state
     (store/transact state (colls/minto cloned-view cloned-child-props cloned-propagated-child-props))}))
