(ns ^{:doc "Store with history"}

  orgpad.core.store

  (:require
   [datascript          :as d]
   [orgpad.tools.search :as search]
   [orgpad.tools.time   :as time]
   [orgpad.tools.datom  :as dtool]))

(defn new-store
  "Creates new store connected to datascript db throught 'conn'"
  [conn]

  (let [history (atom [])
        history-finger (atom nil)]
    (d/listen!
     conn (fn [tx-report]
            (when (not (-> tx-report :tx-mata :undo-redo))
              (reset! history-finger nil))
            (swap! history conj [(time/now) (:tx-data tx-report)])))
    {:state conn
     :history history
     :history-finger history-finger}))

(defn undo!
  "Performs undo on 'store'"
  [store]

  (let [finger         @(:history-finger store)
        history        (-> store :history deref)
        current-finger (if (nil? finger)
                         (-> history count dec)
                         finger)
        tx             (if (neg? current-finger)
                         nil
                         (history current-finger))]
    (when (-> tx nil? not)
      (swap! (:history-finger store) dec)
      (d/transact! (:state store) (dtool/revert-transaction (tx 1)) {:undo-redo true}))))

(defn redo!
  "Performs redo on 'store'"
  [store]

  (let [finger         @(:history-finger store)
        history        (-> store :history deref)
        tx             (if (or (nil? finger)
                               (= (-> history count dec)
                                  finger))
                         nil
                         (history (inc finger)))]
    (when (-> tx nil? not)
      (swap! (:history-finger store) inc)
      (d/transact! (:state store) (tx 1) {:undo-redo true}))))
