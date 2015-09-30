(ns ^{:doc "Datascript datom tools"}

  orgpad.tools.datom

  (:require    [datascript.core          :as ds]))


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
