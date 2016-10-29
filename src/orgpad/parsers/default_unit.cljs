(ns ^{:doc "Definition of default unit parser"}
  orgpad.parsers.default-unit
  (:require [orgpad.core.store :as store]
            [orgpad.tools.dscript :as ds]
            [orgpad.tools.colls :as colls]
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
  [{ :keys [key] } _ _]
  key)


;;; General method declaration

(defmulti read dispatch3)
(defmulti mutate dispatch3)
(defmulti updated? dispatch3key)

;;; default read method

(defn- make-view-type-name-filter
  [type view-type view-name]
  (fn [u]
    (let [res
          (and u
               (= (u :orgpad/view-type) view-type)
               (= (u :orgpad/type) type)
               (= (u :orgpad/view-name) view-name))]
      res)))

(defn- get-view-props
  [unit {:keys [orgpad/view-type orgpad/view-name orgpad/type]}]
  (ds/find-props unit (make-view-type-name-filter type view-type view-name)))

(defn- get-path-info
  [unit view-path]
  (ds/find-props unit (fn [u]
                        (and (= (u :orgpad/view-path) view-path)
                             (= (u :orgpad/type) :orgpad/unit-path-info)))))

(defmethod read :orgpad/unit-view
  [{ :keys [state props old-node tree unit-id view-name view-type view-path view-contexts] :as env } k params]
;;  (println "read :orgpad/unit-view" unit-id view-name view-type view-path view-contexts k)

  (let [db  state

        unit
        (store/query db [:entity unit-id])

        path-info
        (get-path-info unit view-path)

        path-info'
        (assoc (or path-info { :orgpad/view-name view-name
                               :orgpad/view-type view-type
                               :orgpad/view-path view-path })
               :orgpad/type :orgpad/unit-view)

        view-info
        (registry/get-component-info (path-info' :orgpad/view-type))

        view-unit-local
        (get-view-props unit path-info')

        view-unit
        (or view-unit-local (-> view-info :orgpad/default-view-info (assoc :orgpad/refs [{ :db/id unit-id }])))

        props-info
        (when (:orgpad/child-props-types view-info)
          (into [] (map (fn [type]
                          { :orgpad/view-type type
                            :orgpad/view-name (:orgpad/view-name path-info')
                            :orgpad/type      :orgpad/unit-view-child }))
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

        parser'
        (fn [u old-node]
          (if (and old-node
                   (not (or (old-node :changed?)
                            (old-node :me-changed?)))
                   (= (u :db/id) (-> old-node :value :unit :db/id)))

            (do
              ;; (println "skipping" old-node u)
              (vswap! tree conj old-node)
              (old-node :value))
            (props (merge env
                          { :unit-id    (u :db/id)
                            :old-node   nil
                            :view-path  (-> view-path (conj unit-id) (conj (view-unit :orgpad/view-name))) 
                            :view-name  (-> view-info :orgpad/child-default-view-info :orgpad/view-name)
                            :view-type  (-> view-info :orgpad/child-default-view-info :orgpad/view-type)
                            :view-contexts view-contexts' })
                   :orgpad/unit-view params)))

        unit'
        (if (:orgpad/needs-children-info view-info)
          (let [old-children-nodes (and old-node (old-node :children))
                use-children-nodes? (and old-node
                                         (= (old-node :key) :orgpad/unit-view)
                                         (= (count old-children-nodes) (count (unit :orgpad/refs))))]
            (update-in (ds/entity->map unit) [:orgpad/refs]
                       (if use-children-nodes?
                         #(into [] (map parser' % old-children-nodes))
                         #(into [] (map parser' %)))))
          (ds/entity->map unit))

        props
        (when view-contexts
          (mapv #(get-view-props unit %) view-contexts))
        ]

;;     (println { :unit unit'
;;                :path-info path-info'
;;                :view view-unit
;;                :props props })


    { :unit unit'
      :path-info path-info'
      :view view-unit
      :props props }))

;;; Default updated? definition

(defmethod updated? :default
  [_ _ _]
  false)

(defmethod updated? :orgpad/unit-view
  [{:keys [value]} { :keys [state] } force-update-part]

  (let [unit (value :unit)]
    (or (force-update-part (unit :db/id))
        (store/changed? state [:entities (concat [unit] (unit :orgpad/props-refs))]))))

;;; Clone of unit view

(defn- clone-view
  [{:keys [unit view]} new-view-name indexer]
  [(merge view { :db/id (vswap! indexer dec)
                 :orgpad/type :orgpad/unit-view
                 :orgpad/refs (unit :db/id)
                 :orgpad/view-name new-view-name })
   [:db/add (unit :db/id) :orgpad/props-refs @indexer]])

(defn- clone-props
  [refs view-name view-type type indexer new-view-name]
  (let [filter-fn (make-view-type-name-filter type view-type view-name)
        xform
        (comp
         (mapcat #(filter filter-fn (% :props)))
         (mapcat (fn [u] [(merge u { :db/id (vswap! indexer dec)
                                     :orgpad/refs (mapv :db/id (u :orgpad/refs))
                                     :orgpad/view-name new-view-name })
                          [:db/add (-> u :orgpad/refs first :db/id) :orgpad/props-refs @indexer]])))]
    (into [] xform refs)))

(defn- clone-child-props
  [info {:keys [unit view]} new-view-name indexer]
  (if (info :orgpad/needs-children-info)
    (into []
          (mapcat
           (fn [type]
             (clone-props (unit :orgpad/refs) (view :orgpad/view-name) type
                          :orgpad/unit-view-child indexer new-view-name)))
          (info :orgpad/child-props-types))
    []))

(defn- clone-propagated-child-props
  [info {:keys [unit view]} new-view-name indexer]
  (if (info :orgpad/needs-children-info)
    (into []
          (mapcat (fn [u-tree]
                    (into []
                          (mapcat
                           (fn [type]
                             (clone-props (-> u-tree :unit :orgpad/refs) (view :orgpad/view-name)
                                          type
                                          :orgpad/unit-view-child-propagated indexer new-view-name)))
                          (info :orgpad/child-props-types))))
          (unit :orgpad/refs))
    []))

(defmethod mutate :orgpad.units/clone-view
  [{:keys [state]} _ [unit-tree new-view-name]]
  (let [indexer (volatile! 0)
        info (registry/get-component-info (-> unit-tree :view :orgpad/view-type))
        cloned-view (clone-view unit-tree new-view-name indexer)
        cloned-child-props (clone-child-props info unit-tree new-view-name indexer)
        cloned-propagated-child-props (clone-propagated-child-props info unit-tree new-view-name indexer)]
    { :state
      (store/transact state (colls/minto cloned-view cloned-child-props cloned-propagated-child-props)) }))
