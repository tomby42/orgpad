(ns ^{:doc "Core orgpad functionality"}
  orgpad.core.orgpad
  (:require 
   [datascript.core   :as d]
   [orgpad.core.store :as store]))

(def orgpad-schema 
  {
   :root-unit   {}
   :atomic      {}
   :refs        {:db/valueType :db.type/ref
                 :db/cardinality :db.cardinality/many}
   :unit-id     {:db/valueType :db.type/ref}})

(defn empty-orgpad
  []

  (-> orgpad-schema
      d/empty-db
      store/new-datom-store
      (store/transact [{:db/id 0, :root-unit true}])))
