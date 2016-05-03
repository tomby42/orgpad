(ns ^{:doc "Definition of default parser"}
  orgpad.parsers.default
  (:require [orgpad.core.store :as store]
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
  [db query unit-id info]
  (store/query db '[:find [(pull ?v ?selector) ...]
                          :in $ ?selector ?e ?t ?n ?vt
                          :where
                          [?v :orgpad/refs ?e]
                          [?v :orgpad/view-type ?t]
                          [?v :orgpad/view-name ?n]
                          [?v :orgpad/type ?vt]]
               [query unit-id (info :orgpad/view-type) (info :orgpad/view-name) (info :orgpad/type)]))

(defmethod read :orgpad/unit-view
  [{ :keys [state props unit-id view-name view-type view-path view-contexts] :as env } k params]
  (println "read :orgpad/unit-view" unit-id view-name view-type view-path view-contexts k)

  (let [db  state

        [path-info]
        (store/query db '[:find [(pull ?v [ :db/id :orgpad/view-name
                                            :orgpad/view-type :orgpad/view-path ]) ...]
                          :in $ ?e ?p
                          :where
                          [?v :orgpad/type :orgpad/unit-path-info]
                          [?v :orgpad/refs ?e]
                          [?v :orgpad/view-path ?p]] [unit-id view-path])

        path-info'
        (or path-info { :orgpad/view-name view-name
                        :orgpad/view-type view-type
                        :orgpad/view-path view-path
                        :orgpad/type      :orgpad/unit-view})

        view-info
        (registry/get-component-info (path-info' :orgpad/view-type))

        query
        (view-info :orgpad/query)

        [unit]
        (store/query db '[:find [(pull ?e ?selector) ...]
                          :in $ ?selector ?e
                          :where [?e :orgpad/type]] [(query :unit) unit-id])

        [view-unit-local]
        (get-view-props db (query :view) unit-id path-info')

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
        (fn [u]
          (props (merge env
                        { :unit-id    (:db/id u)
                          :view-path  (conj view-path unit-id)
                          :view-name  (-> view-info :orgpad/child-default-view-info :orgpad/view-name)
                          :view-type  (-> view-info :orgpad/child-default-view-info :orgpad/view-type)
                          :view-contexts view-contexts' })
                 :orgpad/unit-view params))

        unit'
        (if (:orgpad/needs-children-info view-info)
          (update-in unit [:orgpad/refs] #(doall (mapv parser' %)))
          unit)

         props
         (when view-contexts
           (doall (mapv (fn [context]
                          (get-view-props db (context :orgpad/query) unit-id
                                          context))
                        view-contexts)))
        ]

    (println { :unit unit'
               :path-info path-info'
               :view view-unit
               :props props })

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

  (or
   (store/changed? state
                   '[ :find [?k ?v]
                      :in $ ?e
                      :where
                      [?e ?k ?v] ]
                   [(-> value :unit :db/id)] )
   (store/changed? state
                   '[ :find [?k ?v]
                      :in $ ?e
                      :where
                      [?e ?k ?v] ]
                   [(-> value :view :db/id)] ) ) )
