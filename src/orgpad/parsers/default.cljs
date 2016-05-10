(ns ^{:doc "Definition of default parser"}
  orgpad.parsers.default
  (:require [orgpad.core.store :as store]
            [orgpad.tools.dscript :as ds]
            [orgpad.components.registry :as registry]))


;;; Dispatch definitions

(declare read)
(declare mutate)
(declare updated?)

(defn- dispatch3
  "Helper function for implementing :read and :mutate as multimethods. Use this
   as the dispatch-fn."
  [_ key _]
  key)

(defn- dispatch2
  "Helper function for implementing :update? as multimethod. Use this
   as the dispatch-fn."
  [{ :keys [key] } _]
  key)


;;; General method dclaration

(defmulti read dispatch3)
(defmulti mutate dispatch3)
(defmulti updated? dispatch2)


;;; default read method

(defn- get-view-props
  [unit {:keys [orgpad/view-type orgpad/view-name orgpad/type]}]
  (ds/find-props unit (fn [u]
                        (and (= (u :orgpad/view-type) view-type)
                             (= (u :orgpad/type) type)
                             (= (u :orgpad/view-name view-name))))))

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
        (or path-info { :orgpad/view-name view-name
                        :orgpad/view-type view-type
                        :orgpad/view-path view-path
                        :orgpad/type      :orgpad/unit-view})

        view-info
        (registry/get-component-info (path-info' :orgpad/view-type))

        view-unit-local
        (get-view-props unit path-info')

        view-unit
        (or view-unit-local (:orgpad/default-view-info view-info))

        props-info
        (when (:orgpad/child-props-type view-info)
          { :orgpad/view-type (:orgpad/child-props-type view-info)
            :orgpad/view-name (:orgpad/view-name path-info')
            :orgpad/query     (-> view-info :orgpad/query :child-props)
            :orgpad/type      :orgpad/unit-view-child })

        view-contexts'
        (if (:orgpad/propagate-props-from-childs view-info)
          (let [view-contexts'' (mapv #(assoc % :orgpad/type :orgpad/unit-view-child-propagated)
                                      view-contexts)]
            (if props-info
              (conj view-contexts'' props-info)
              view-contexts''))
          (when props-info
            [props-info]))

        parser'
        (fn [u old-node]
          (if (and old-node
                   (not (or (old-node :changed?)
                            (old-node :me-changed?))))
            (do
              (println "skipping" old-node u)
              (vswap! tree conj old-node)
              (old-node :value))
            (props (merge env
                          { :unit-id    (u :db/id)
                            :view-path  (conj view-path unit-id)
                            :view-name  (-> view-info :orgpad/child-default-view-info :orgpad/view-name)
                            :view-type  (-> view-info :orgpad/child-default-view-info :orgpad/view-type)
                            :view-contexts view-contexts' })
                   :orgpad/unit-view params)))

        unit'
        (if (:orgpad/needs-children-info view-info)
          (let [old-children-nodes (and old-node (old-node :children))
                use-children-nodes? (= (count old-children-nodes) (count (unit :orgpad/refs)))]
            (update-in (ds/entity->map unit) [:orgpad/refs]
                       (if use-children-nodes?
                         #(into [] (map parser' % old-children-nodes))
                         #(into [] (map parser' %)))))
          unit)

        props
        (when view-contexts
          (doall (mapv (fn [context]
                         (get-view-props unit context))
                       view-contexts)))
        ]

;;     (println { :unit unit'
;;                :path-info path-info'
;;                :view view-unit
;;                :props props })
;;
;;     (println (view-unit :orgpad/transform) (view-unit :db/id))

    { :unit unit'
     :path-info path-info'
     :view view-unit
     :props props }))

;;; Default updated? definition

(defmethod updated? :default
  [_ _]
  false)

(defmethod updated? :orgpad/unit-view
  [{:keys [value]} { :keys [state] }]

  (store/changed? state [:entities (concat [(value :unit)] (-> value :unit :orgpad/props-refs))]))
