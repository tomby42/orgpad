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
   :orgpad/props-refs  {:db/valueType :db.type/ref
                        :db/cardinality :db.cardinality/many}
   :orgpad/view-name   {}
   :orgpad/view-names  {:db/cardinality :db.cardinality/many}
   :orgpad/view-type   {}
   :orgpad/view-types  {:db/cardinality :db.cardinality/many}
   :orgpad/view-path   {}
   :orgpad/view-paths  {:db/cardinality :db.cardinality/many}
   :orgpad/transform   {}
   :orgpad/active-unit {}
   :orgpad/unit-width  {}
   :orgpad/unit-height {}
   :orgpad/unit-border-color {}
   :orgpad/unit-bg-color {}
   :orgpad/unit-border-width {}
   :orgpad/unit-corner-x {}
   :orgpad/unit-corner-y {}
   :orgpad/unit-border-style {}
   })

(defn empty-orgpad-db
  []

  (-> (store/new-datom-atom-store {} (d/empty-db orgpad-db-schema))
      (store/transact [{ :db/id 0,
                         :orgpad/atom "root"
                         :orgpad/props-refs 1
                         :orgpad/type :orgpad/root-unit }
                       { :db/id 1,
                         :orgpad/type :orgpad/root-unit-view,
                         :orgpad/refs 0 }
                       ])
      (store/transact [[:mode] :write])))
