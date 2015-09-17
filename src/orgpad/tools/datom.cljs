(ns ^{:doc "Datascript datom tools"}

  orgpad.tools.datom

  (:require    [datascript          :as d]))

(defn revert-transaction 
  "Returns transaction data for reverting changes made by transaction 'tx-data'"
  [tx-data]
  (map (fn [{:keys [e a v t added]}] (d/Datom. e a v t (not added))) tx-data))
