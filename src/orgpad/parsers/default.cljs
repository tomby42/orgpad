(ns ^{:doc "Definition of default parser"}
  orgpad.parsers.default
  (:require [om.next :as om :refer-macros [defui]]
            [orgpad.core.store :as store]
            [orgpad.components.node :as node]
            [orgpad.components.registry :as registry]))

(declare read)

(defn- dispatch
  "Helper function for implementing :read and :mutate as multimethods. Use this
   as the dispatch-fn."
  [_ key _]
  (if (get-method read key)
    key
    :orgpad/unit-view))

(defmulti read dispatch)

(defmulti mutate om/dispatch)

(defn get-sub-query
  [query tag]
  (->> query (drop-while #(= (tag %) nil)) first tag))

(defmethod read :orgpad/unit-view
  [{:keys [state parser query-root view-path unit-id view-type] :as env} k _]
  (println "read :orgpad/unit-view" query-root view-path unit-id view-type k)
  (if (not= k (first query-root))
    {:value nil}
    (let [qry (second query-root)
          db  @state
          [unit]
          (store/query db '[:find [(pull ?e ?selector) ...]
                            :in $ ?selector ?e
                            :where [?e :orgpad/type]] [(get-sub-query qry :unit) unit-id])

          view-info
          (registry/get-component-info view-type)

          [view-unit-local]
          (store/query db '[:find [(pull ?v ?selector) ...]
                            :in $ ?selector ?e ?t ?p
                            :where
                            [?v :orgpad/refs ?e]
                            [?v :orgpad/view-type ?t]
                            [?v :orgpad/view-path ?p]]
                       [(get-sub-query qry :view) unit-id view-type view-path])

          view-unit
          (or view-unit-local (:orgpad/default-view-info view-info))

          view-unit-info
          (registry/get-component-info (:orgpad/view-type view-unit))

          view-query
          ;; (-> view-unit-info :orgpad/class om/get-query)
          (om/get-query node/Node)

          parser'
          (fn [u]
            (parser (merge env {:view-path  (conj view-path (:db/id unit))
                                :unit-id    (:db/id u)
                                :view-type  (:orgpad/view-type view-unit)}) view-query))

          unit'
          (if (:orgpad/needs-children-info view-unit-info)
            (update-in unit [:orgpad/refs] #(map parser' %))
            unit)
          ]

      (println "result" {:value {:unit unit' :view view-unit}})

      {:value {:unit unit' :view view-unit}})))
