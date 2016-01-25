(ns ^{:doc "Definition of default parser"}
  orgpad.parsers.default
  (:require [om.next :as om :refer-macros [defui]]
            [orgpad.core.store :as store]
            [orgpad.components.queries :as qs]
            [orgpad.components.registry :as registry]))

(defmulti read om/dispatch)

(defmulti write om/dispatch)

(defn get-sub-query
  [query tag]
  (->> query (drop-while #(= (tag %) nil)) first tag))

(defmethod read :orgpad/unit-view
  [{:keys [state parser query view-path unit-id view-type] :as env} k _]
  (println "read :orgpad/unit-view" query view-path unit-id view-type)
  (let [db  @state
        [unit]
        (store/query db '[:find [(pull ?e ?selector) ...]
                          :in $ ?selector ?e
                          :where [?e :orgpad/type]] [(get-sub-query query :unit) unit-id])

        view-info
        (registry/get-component-info view-type)

        [view-unit-local]
        (store/query db '[:find [(pull ?v ?selector) ...]
                          :in $ ?selector ?e ?t ?p
                          :where
                          [?v :orgpad/refs ?e]
                          [?v :orgpad/view-type ?t]
                          [?v :orgpad/view-path ?p]]
                     [(get-sub-query query :view) unit-id view-type view-path])

        view-unit
        (or view-unit-local (:orgpad/default-view-info view-info))

        view-unit-info
        (registry/get-component-info (:orgpad/view-type view-unit))

        view-query
        (-> view-unit-info :orgpad/class om/get-query)

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

    {:value {:unit unit' :view view-unit}}))
