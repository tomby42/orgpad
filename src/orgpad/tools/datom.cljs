(ns ^{:doc "Datascript datom tools"}
  orgpad.tools.datom
  (:require    [datascript.core          :as ds]
               [datascript.db            :as db]))

(defn datoms->rev-tx
  "Returns transaction data for reverting changes made by transaction 'tx-data'"
  [tx-data]
  (map (fn [d] (if (.-added d)
                 [:db/retract (.-e d) (.-a d) (.-v d)]
                 [:db/add (.-e d) (.-a d) (.-v d)]))
       tx-data))

(defn datoms->tx
  "Returns transaction data for changes made by transaction 'tx-data'"
  [tx-data]
  (map (fn [d] (if (.-added d)
                 [:db/add (.-e d) (.-a d) (.-v d)]
                 [:db/retract (.-e d) (.-a d) (.-v d)]))
       tx-data))

(defn rev-datoms
  "Returns datoms reverting changes made by transaction data 'tx-data'"
  [tx-data]
  (map (fn [d] (db/datom (.-e d) (.-a d) (.-v d) (.-tx d) (.-added d)))
       tx-data))

(defn datom->vec
  [d]
  [(.-e d) (.-a d) (.-v d) (.-tx d) (.-added d)])

(defn- update-old-uid->new-uid
  "Generate new ids for all old ids that are not in mapping"
  [datom-seq old-uid->new-uid newid]
  (->> datom-seq
       (reduce (fn [o->n datom]
                 (let [uid (.-e datom)]
                   (if (o->n uid)
                     o->n
                     (assoc! o->n uid (newid uid)))))
               (transient old-uid->new-uid))
       persistent!))

(defn remap-datom-seq-eids
  "Remaps all occurrences of old eid to new one"
  [datom-seq old-uid->new-uid newid remap-datom]
  (let [o->n (update-old-uid->new-uid datom-seq old-uid->new-uid newid)]
    {:datoms (map (partial remap-datom o->n) datom-seq)
     :mapping o->n}))
