(ns ^{:doc "Core orgpad functionality"}
  orgpad.core.orgpad
  (:require
   [datascript.core   :as d]
   [orgpad.core.store :as store]))

(def orgpad-db-schema
  {
   :orgpad/type        {}
   :orgpad/atom        {}
   :orgpad/tags        {:db/cardinality :db.cardinality/many}
   :orgpad/desc        {}
   :orgpad/refs        {:db/valueType :db.type/ref
                        :db/cardinality :db.cardinality/many}
   :orgpad/view-path   {}
   :orgpad/view-type   {}
   })

(defn empty-orgpad-db
  []

  (-> orgpad-db-schema
      d/empty-db
      store/new-datom-store
      (store/transact [{:db/id 0,
                        :orgpad/atom "root"
                        :orgpad/type :orgpad/root-unit}
                       {:db/id 1,
                        :orgpad/type :orpad/root-unit-view,
                        :orgpad/refs 0,
                        :orgpad/view-type :atomic-view}
                       ])))
