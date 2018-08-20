(ns ^{:doc "Store with history"}
  orgpad.core.store
  (:require
   [datascript.core     :as d]
   [com.rpl.specter     :as s :refer-macros [select transform]]
   [orgpad.tools.datom  :as dtool]))

(defprotocol IStore

  (query [store qry] [store qry params]
    "Query store by 'qry' with given params and returns result.")
  (transact [store qry] [store qry params]
    "Perform transaction on store and returns new one."))

(defprotocol IStoreChanges

  (changed? [store qry] [store qry params]
    "Returns true if 'qry' matches store parts that were changed from
    last call of reset-changes")
  (changed-entities [store]
    "Returns collection of ids of changed entities")
  (reset-changes [store]
    "Returns new store with reset cumulative changes")
  (cumulative-changes [store]
    "Returns cumulative changes from last call of reset-changes"))

(defprotocol IStoreHistory

  (undo [store]
    "Perform undo on store and returns new one.")
  (redo [store]
    "Performs redo on store and returns new one.")
  (tag [store tag]
    "Tag current history position.")
  (with-history-mode [store mode]
    "Sets history mode - :acc concat record to last recornd in history, :add add new record to the end"))

(defprotocol IStoreHistoryInfo

  (history-info [store]
    "Returns history info. [finger, count]"))

(defprotocol IStoreHistoryStatus

  (undoable? [store]
    "Returns true if it is possible to perform undo on current store.
     false otherwise.")

  (redoable? [store]
    "Returns true if it is possible to perform redo on current store.
     false otherwise."))

(defprotocol IHistoryRecords

  (push [this record] [this record meta_]
    "Returns new hitory record with 'record' and 'meta' added to history")

  (undo-info [this]
    "Returns info for performing undo")

  (redo-info [this]
    "Returns info for performing redo")

  (tag-finger [this tag]
    "Tag last record")

  (set-history-mode [this mode]
    "Sets history mode"))

(defprotocol ITempids

  (tempids [this]
    "Returns current mapping of tempids to ids from last transaction
    that created current state"))

;;; History record

(deftype HistoryRecords [history history-finger]

  IHistoryRecords
  (push
    [this record]
    (push this record nil))

  (push
    [this record meta_]
    (let [mode (-> history meta :mode)]
      (if (empty? record)
        this
        (if (= mode :acc)
          (HistoryRecords. (s/setval [s/LAST s/END] record history) meta_)
          (HistoryRecords. (conj (.-history this) record) meta_)))))

  (undo-info [this]
    (let [finger         (.-history-finger this)
          history        (.-history this)
          current-finger (if (nil? finger)
                           (-> history count dec)
                           finger)
          record         (if (neg? current-finger)
                           nil
                           (history current-finger))]
      (if (nil? record)
        [nil nil]
        [record (dec current-finger)])))

  (redo-info [this]
    (let [finger         (.-history-finger this)
          history        (.-history this)
          record         (if (or (nil? finger)
                                 (= (-> history count dec)
                                    finger))
                           nil
                           (history (inc finger)))]
      (if (nil? record)
        [nil nil]
        [record (inc finger)])))

  (tag-finger [this tag]
    (let [pos (if (nil? history-finger)
                s/LAST
                (if (neg? history-finger)
                  0
                  history-finger))]
      (HistoryRecords. (s/transform [pos] #(with-meta % tag) history) history-finger)))

  (set-history-mode [this mode]
    (let [history' (if (:new-record mode)
                     (conj history [])
                     history)]
      (HistoryRecords. (with-meta history'
                         (assoc (or (meta history) {}) :mode (or (:mode mode) mode)))
                       history-finger)))

  IEquiv
  (-equiv [record other]
    (and (= (.-history record) (.-history other))
         (= (.-history-finger record) (.-history-finger other))))
)

(defn- new-history-records
  [& [history-records]]
  (HistoryRecords. (or history-records []) nil))

;;; DatomStore

(declare DatomStore)

(defn- tx-report->store
  "Returns new datom store with applied data from 'tx-report'"
  [store tx-report & [finger]]
  (let [tx-data (:tx-data tx-report)]
    ;; (println tx-report)
    (DatomStore. (:db-after tx-report)
                 (push (.-history-records store) tx-data finger)
                 (if (empty? tx-data)
                   (.-cumulative-changes store)
                   (concat (.-cumulative-changes store) tx-data))
                 (if (empty? tx-data)
                   (.-changed-entities store)
                   (reduce (fn [ens d] (conj ens (.-e d))) (.-changed-entities store) tx-data))
                 (merge (.-meta_ store)
                        { :orgpad.store/tempids (:tempids tx-report) }))))

(defn- tx-report->only-db-store
  "Returns new datom store with new db only, rest of properties are preserved if not required to update in params"
  [store tx-report params]
  (let [tx-data (:tx-data tx-report)]
    (DatomStore. (:db-after tx-report)
                 (.-history-records store)
                 (if (and (:cumulative-changes params) (not (empty? tx-data)))
                   (concat (.-cumulative-changes store) tx-data)
                   (.-cumulative-changes store))
                 (if (and (:cumulative-changes params) (not (empty? tx-data)))
                   (reduce (fn [ens d] (conj ens (.-e d))) (.-changed-entities store) tx-data)
                   (.-changed-entities store))
                 (if (:tempids params)
                   (merge (.-meta_ store)
                          {:orgpad.store/tempids (:tempids tx-report)})
                   (.-meta_ store)))))

(deftype DatomStore [db history-records cumulative-changes changed-entities meta_]

  IWithMeta
  (-with-meta [_ new-meta] (DatomStore. db history-records cumulative-changes changed-entities new-meta))

  IMeta
  (-meta [_] meta_)

  IStore
  (query
    [store qry]
    (if (= (first qry) :entity)
      (d/entity (.-db store) (second qry))
      (d/q qry (.-db store))))

  (query
    [store qry params]
    (apply d/q qry (.-db store) params))

  (transact
    [store qry]
    (tx-report->store store (d/with (.-db store) qry)))

  (transact
    [store qry params]
    (tx-report->only-db-store store (d/with (.-db store) qry) params))

  IStoreChanges
  (changed?
    [store qry]
    (if (= (first qry) :entities)
      (loop [ents (second qry)]
        (if (empty? ents)
          false
          (if (contains? (.-changed-entities store) ((first ents) :db/id))
            true
            (recur (rest ents)))))
      (-> qry (d/q (.-cumulative-changes store)) empty? not)))

  (changed?
    [store qry params]
    (-> (apply d/q qry (.-cumulative-changes store) params) empty? not))

  (changed-entities
    [store]
    (.-changed-entities store))

  (reset-changes
    [store]
    (DatomStore. (.-db store)
                 (.-history-records store)
                 []
                 #{}
                 (.-meta_ store)))

  (cumulative-changes
    [store]
    (.-cumulative-changes store))

  IStoreHistory
  (undo
    [store]
    (let [[tx finger] (undo-info (.-history-records store))]
      (if (-> tx nil? not)
        (tx-report->store store (d/with (.-db store)
                                        (dtool/datoms->rev-tx tx))
                          finger)
        store)))

  (redo
    [store]
    (let [[tx finger] (redo-info (.-history-records store))]
      (if (-> tx nil? not)
        (tx-report->store store (d/with (.-db store)
                                        (dtool/datoms->tx tx))
                          finger)
        store)))

  (tag
    [store tag]
    (DatomStore. db (tag-finger history-records tag) cumulative-changes changed-entities (.-meta_ store)))

  (with-history-mode
    [store mode]
    (DatomStore. db (set-history-mode history-records mode) cumulative-changes changed-entities (.-meta_ store)))

  IStoreHistoryInfo
  (history-info
    [store]
    [(-> store .-history-records .-history-finger) (-> store .-history-records .-history count)])

  IStoreHistoryStatus
  (undoable?
    [store]
    (-> (undo-info (.-history-records store)) first nil? not))

  (redoable?
    [store]
    (-> (redo-info (.-history-records store)) first nil? not))

  ITempids
  (tempids
    [store]
    (-> store .-meta_ :orgpad.store/tempids))

  IPrintWithWriter
  (-pr-writer
    [store writer opts]
    (-write writer "#orgpad/DatomStore {")
    (-write writer ":db ")
    (pr-writer (.-db store) writer opts)
    (-write writer "}"))

  IEquiv
  (-equiv
      [store other]
    (and (= (.-db store) (.-db other))
         (= (.-history-records store) (.-history-records other))
         (= (.-cumulative-changes store) (.-cumulative-changes other))
         (= (.-changed-entities store) (.-changed-entities other))
         (= (.-meta_ store) (.-meta_ other))))
  )

(defn new-datom-store
  "Creates new datom store with initial 'db' and optional history 'history-records'"
  [db & [history-records]]

  (DatomStore. db (new-history-records history-records) [] #{} nil))

(defn datom-store-from-reader
  "Creates new datom store from reader value"
  [{:keys [db]}]
  (new-datom-store db))

;;; DatomAtomStore - datom remembers history but atom not

(defn- datom-query?
  "Returns true if 'qry' is datascript query"
  [qry]
  (let [f (first qry)]
    (or (= :find f) (= :entity f) (= :entities f))))

(defn- fnamespace
  [x]
  (when (and (-> x nil? not)
             (keyword? x))
    (namespace x)))

(defn- datom-transact-query?
  "Returns true if 'qry' is datascript transaction query"
  [qry]
  (let [f (first qry)]
    (or (= f :entities)
        (contains? f :db/id)
        (= (-> f first fnamespace) "db")
        (= (-> f first fnamespace) "db.fn")
        (= (-> f type) datascript.db/Datom))))

(defn- stransact
  "specter transact"
  [store [path action]]
  (with-meta
    (if (fn? action)
      (s/transform path action store)
      (s/setval path action store))
    {:changed? true}))

(deftype DatomAtomStore [datom atom meta_]

  IWithMeta
  (-with-meta [_ new-meta] (DatomAtomStore. datom atom new-meta))

  IMeta
  (-meta [_] meta_)

  IStore
  (query
    [store qry]
    (if (datom-query? qry)
      (query (.-datom store) qry)
      (s/select qry (.-atom store) )) )

  (query
    [store qry params]
    (if (datom-query? qry)
      (query (.-datom store) qry params)
      (s/select qry (.-atom store)) ))

  (transact
    [store qry]
    (if (datom-transact-query? qry)
      (DatomAtomStore. (transact (.-datom store) qry) (.-atom store) (.-meta_ store))
      (DatomAtomStore. (.-datom store) (stransact (.-atom store) qry) (.-meta_ store))))

  (transact
    [store qry params]
    (if (datom-transact-query? qry)
      (DatomAtomStore. (transact (.-datom store) qry params) (.-atom store) (.-meta_ store))
      (DatomAtomStore. (.-datom store) (stransact (.-atom store) qry) (.-meta_ store))))

  IStoreChanges
  (changed?
    [store qry]
    (if (datom-query? qry)
      (changed? (.-datom store) qry)
      true))

  (changed?
    [store qry params]
    (if (datom-query? qry)
      (changed? (.-datom store) qry params)
      true))

  (changed-entities
    [store]
    {:datom (changed-entities (.-datom store)) :atom :all})

  (reset-changes
    [store]
    (DatomAtomStore. (reset-changes (.-datom store))
                     (with-meta (.-atom store) nil)
                     (.-meta_ store)))

  (cumulative-changes
    [store]
    (let [ameta (meta (.-atom store))]
      {:datom (cumulative-changes (.-datom store))
       :atom (if (and ameta (:changed? ameta))
               (.-atom store)
               nil)}))

  IStoreHistory
  (undo
    [store]
    (DatomAtomStore. (undo (.-datom store))
                     (.-atom store)
                     (.-meta_ store)))

  (redo
    [store]
    (DatomAtomStore. (redo (.-datom store))
                     (.-atom store)
                     (.-meta_ store)))

  (tag
    [store tag_]
    (DatomAtomStore. (tag (.-datom store) tag_)
                     (.-atom store)
                     (.-meta_ store)))

  (with-history-mode
    [store mode]
    (DatomAtomStore. (with-history-mode (.-datom store) mode)
                     (.-atom store)
                     (.-meta_ store)))

  IStoreHistoryInfo
  (history-info
    [store]
    (history-info (.-datom store)))

  IStoreHistoryStatus
  (undoable?
    [store]
    (undoable? (.-datom store)))

  (redoable?
    [store]
    (redoable? (.-datom store)))

  ITempids
  (tempids
    [store]
    (-> store .-datom tempids))

  IPrintWithWriter
  (-pr-writer
    [store writer opts]
    (-write writer "#orgpad/DatomAtomStore {")
    (-write writer ":datom ")
    (pr-writer (.-datom store) writer opts)
    (-write writer " :atom ")
    (pr-writer (.-atom store) writer opts)
    (-write writer "}"))

  IEquiv
  (-equiv
      [store other]
    (and (= (.-datom store) (.-datom other))
         (= (.-atom store) (.-atom other))
         (= (.-meta_ store) (.-meta_ other))))
  )

(defn new-datom-atom-store
  "Creates new datom-atom store with initial value 'init-value',
  database 'db' and optional history 'history-records'"
  [init-value db & [history-records]]

  (DatomAtomStore. (new-datom-store db history-records)
                   init-value
                   nil))

(defn datom-atom-store-from-reader
  "Creates new datom-atom store from reader value"
  [{:keys [datom atom]}]
  (DatomAtomStore. datom atom nil))

(defn datom-atom-store-ds-db
  "Get Datascript DB from DatomAtomStore"
  [store]
  (-> store .-datom .-db))
