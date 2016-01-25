(ns ^{:doc "Default root read/write parser"}
  orgpad.parsers.default-root
  (:require [om.next :as om]
            [orgpad.core.store :as store]
            [orgpad.parsers.default :as dp :refer [read write]]
            [orgpad.components.queries :as qs]
            [orgpad.components.registry :as registry]))

(defmethod read :orgpad/root-view
  [{:keys [state parser query] :as env} k params]
  (let [db @state
        [root-view-info]
        (store/query db '[:find [(pull ?e [:db/id :orgpad/refs :orgpad/view-type]) ...]
                          :where [?e :orgpad/type :orpad/root-unit-view]])]

    (println "root parser")

    {:value (parser (merge env {:view-path []
                                :unit-id (get-in root-view-info [:orgpad/refs 0 :db/id])
                                :view-type (:orgpad/view-type root-view-info)})
                    [{:orgpad/unit-view query}])}))
