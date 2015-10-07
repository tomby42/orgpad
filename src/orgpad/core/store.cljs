(ns ^{:doc "Store with history"}

  orgpad.core.store

  (:require
   [datascript.core     :as d]
   [orgpad.tools.datom  :as dtool]))

(defprotocol IStore

  (query [store qry]
    "Query store by 'qry' and returns result.")
  (transact [store qry]
    "Perform transaction on store and returns new one."))

(defprotocol IStoreChanges

  (changed? [store qry]
    "Returns true if 'qry' matches store parts that were changed from
    last call of reset-changes")
  (reset-changes [store]
    "Returns new store with reset cumulative changes"))

(defprotocol IStoreHistory

  (undo [store]
    "Perform undo on store and returns new one.")
  (redo [store]
    "Performs redo on store and returns new one."))

;;; DatomStore

(declare DatomStore)

(defn- tx-report->store
  "Returns new datom store with applied data from 'tx-report'"
  [store tx-report & [finger]]
  (let [tx-data (:tx-data tx-report)]
    ;; (println tx-data)
    (DatomStore. (:db-after tx-report)
               (if (empty? tx-data)
                 (.-history store)
                 (conj (.-history store) tx-data))
               (or finger (.-history-finger store))
               (if (empty? tx-data)
                 (.-cumulative-changes store)
                 (concat (.-cumulative-changes store) tx-data)))))

(deftype DatomStore [db history history-finger cumulative-changes]

  IStore
  (query
    [store qry]
    (d/q qry (.-db store)))

  (transact
    [store qry]
    (tx-report->store store (d/with (.-db store) qry)))

  IStoreChanges
  (changed?
    [store qry]
    (-> qry (d/q (.-cumulative-changes store)) empty? not))

  (reset-changes
    [store]
    (DatomStore. (.-db store)
                 (.-history store)
                 (.-history-finger store)
                 []))

  IStoreHistory
  (undo
    [store]

    (let [finger         (.-history-finger store)
          history        (.-history store)
          current-finger (if (nil? finger)
                           (-> history count dec)
                           finger)
          tx             (if (neg? current-finger)
                           nil
                           (history current-finger))]
      (if (-> tx nil? not)
        (tx-report->store store (d/with (.-db store)
                                        (dtool/datoms->rev-tx tx))
                          (dec current-finger))
        store)))

  (redo
    [store]
    (let [finger         (.-history-finger store)
          history        (.-history store)
          tx             (if (or (nil? finger)
                                 (= (-> history count dec)
                                    finger))
                           nil
                           (history (inc finger)))]
      (if (-> tx nil? not)
        (tx-report->store store (d/with (.-db store)
                                        (dtool/datoms->tx tx))
                          (inc finger))
        store)))
  )

(defn new-datom-store
  "Creates new datom store with initial 'db'"
  [db]

  (DatomStore. db [] nil []))
