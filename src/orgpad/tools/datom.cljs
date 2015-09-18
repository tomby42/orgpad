(ns ^{:doc "Datascript datom tools"}

  orgpad.tools.datom

  (:require    [datascript          :as ds]))

(defn revert-transaction 
  "Returns transaction data for reverting changes made by transaction 'tx-data'"
  [tx-data]
  (map (fn [d] (if (.-added d)
                 [:db/retract (.-e d) (.-a d) (.-v d)]
                 [:db/add (.-e d) (.-a d) (.-v d)])) 
       tx-data))
