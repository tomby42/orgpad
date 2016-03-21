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

(defmethod read :orgpad/unit-view
  [{ :keys [state props view-path unit-id view-type] :as env } k params]
  (println "read :orgpad/unit-view" view-path unit-id view-type k)

  (let [db  state

        view-info
        (registry/get-component-info view-type)

        query
        (view-info :orgpad/query)

        [unit]
        (store/query db '[:find [(pull ?e ?selector) ...]
                          :in $ ?selector ?e
                          :where [?e :orgpad/type]] [(query :unit) unit-id])


        [view-unit-local]
        (store/query db '[:find [(pull ?v ?selector) ...]
                          :in $ ?selector ?e ?t ?p
                          :where
                          [?v :orgpad/refs ?e]
                          [?v :orgpad/view-type ?t]
                          [?v :orgpad/view-path ?p]]
                     [(query :view) unit-id view-type view-path])

        view-unit
        (or view-unit-local (:orgpad/default-view-info view-info))

        view-unit-info
        (registry/get-component-info (:orgpad/view-type view-unit))

        parser'
        (fn [u]
          (props (merge env
                        { :view-path  (conj view-path (:db/id unit))
                          :unit-id    (:db/id u)
                          :view-type  (:orgpad/view-type view-unit) })
                 :orgpad/unit-view params))

        unit'
        (if (:orgpad/needs-children-info view-unit-info)
          (update-in unit [:orgpad/refs] #(doall (mapv parser' %)))
          unit)
        ]

    (println { :unit unit'
               :view view-unit })

    { :unit unit'
      :view view-unit }))


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
