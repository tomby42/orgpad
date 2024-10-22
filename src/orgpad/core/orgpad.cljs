(ns ^{:doc "Core orgpad functionality"}
  orgpad.core.orgpad
  (:require
   [cljs.reader :as reader]
   [datascript.core   :as d]
   [orgpad.core.store :as store]
   [orgpad.core.orgpad-def :as od :refer [orgpad-db-schema]]
   [orgpad.tools.colls :as colls]
   [orgpad.tools.orgpad :as ot]
   [orgpad.tools.datom :as datom]
   [orgpad.tools.dscript :as ds]
   [orgpad.components.registry :as cregistry]
   [ajax.core :as ajax]
   [goog.string :as gstring]
   [goog.string.format]))

(def root-entity-id od/root-entity-id)

(defn default-styles-qry
  []
  (let [counter (volatile! 0)]
    (into [] (comp
              (filter :orgpad/child-props-style-types)
              (mapcat (fn [cdef]
                        (map #(assoc (get-in cdef [:orgpad/child-props-default (:key %)])
                                     :db/id (vswap! counter dec))
                             (:orgpad/child-props-style-types cdef)))))
          (vals (cregistry/get-registry)))))

(defn db-contains-styles?
  [db]
  (let [styles (into #{}
                     (comp
                      (mapcat :orgpad/child-props-style-types)
                      (map :key))
                     (vals (cregistry/get-registry)))]
    (-> db
        (store/query '[:find ?e
                       :in $ ?contains
                       :where
                       [?e :orgpad/view-type ?vt]
                       [(?contains ?vt)]]
                     [#(contains? styles %)])
        empty?
        not)))

(defn insert-default-styles
  [db]
  (let [counter (volatile! 0)
        qry (default-styles-qry)]
    (store/transact db qry {})))

(defn empty-orgpad-db
  []
  (-> (store/new-datom-atom-store {:app-state {:mode :write
                                               :quickedit-when-created? false}}
                                  (d/empty-db orgpad-db-schema))
      (store/transact [{:db/id root-entity-id
                        :orgpad/props-refs (inc root-entity-id)
                        :orgpad/type :orgpad/root-unit}
                       {:db/id (inc root-entity-id)
                        :orgpad/type :orgpad/root-unit-view,
                        :orgpad/refs root-entity-id}] {})
      insert-default-styles))

(defn- update-refs-orders
  [db]
  (let [refs-orders
        (store/query db
                     '[:find ?eid ?o
                       :in $
                       :where
                       [?eid :orgpad/refs-order ?o]])]

    (mapv (fn [[eid o]]
            [:db/add eid :orgpad/refs-order (apply sorted-set-by colls/first-< o)])
          refs-orders)))

(defn- unescape-atoms
  [db]
  (let [atoms
        (store/query db
                     '[:find ?eid ?a
                       :in $
                       :where
                       [?eid :orgpad/atom ?a]])]
    (mapv (fn [[eid a]]
            [:db/add eid :orgpad/atom (js/window.unescape a)]) atoms)))

(defn- remove-view-stack
  [db]
  (let [stack (store/query db
                           '[:find ?s .
                             :in $
                             :where
                             [1 :orgpad/view-stack ?s]])]
    (if (nil? stack)
      []
      [[:db/retract 1 :orgpad/view-stack stack]])))

(defn- app-state
  [db]
  (-> db (store/query [:app-state]) first))

(defn move-app-state-qry
  [db]
  [[] {:app-state (-> db (store/query []) first)}])

(defn- update-db
  [db]
  (let [qry
        (colls/minto []
                     (update-refs-orders db)
                     (unescape-atoms db)
                     (remove-view-stack db)
                     (when (not (db-contains-styles? db))
                       (default-styles-qry)))
        app-state-qry  (if (app-state db)
                         [[[:app-state :orgpad/view-stack] nil]]
                         (move-app-state-qry db))]
    ;; (js/console.log db)
    (cond-> db
      (-> qry empty? not) (store/transact qry {})
      (-> app-state-qry empty? not) (store/transact app-state-qry))))

(defn- has-root-entity-id-zero?
  [db]
  (-> db
      (store/query
       '[:find ?t
         :in $
         :where
         [0 :orgpad/type ?t]])
      empty?
      not))

(defn- remap-datom
  [o->n datom]
  (ot/remap-datom o->n datom {}))

(defn- remap-db-to-custom-root
  [store root-entity-id]
  (let [db (store/datom-atom-store-ds-db store)
        new-indices (volatile! {})
        index (volatile! (+ root-entity-id 2))
        remap (datom/remap-datom-seq-eids (seq db)
                                          {0 root-entity-id
                                           1 (inc root-entity-id)}
                                          #(let [new-idx (vswap! index inc)]
                                             (vswap! new-indices assoc % new-idx)
                                             new-idx)
                                          remap-datom)]
    (store/new-datom-atom-store (.-atom store)
                                (ds/new-db-with-same-schema db (:datoms remap)))))

(defn- remap-db
  [store]
  (if (has-root-entity-id-zero? store)
    (remap-db-to-custom-root store root-entity-id)
    store))

(defn orgpad-db
  [data]
  (if (and data (-> data .-length (not= 0)))
    (let [d (aget js/LZString "decompressFromBase64")
          raw-data
          (if (.startsWith data "#orgpad/DatomAtomStore")
            data
            (d data))]
      (update-db (-> raw-data cljs.reader/read-string remap-db)))
    (empty-orgpad-db)))

(defn- compress-db
  [db]
  (let [compress (aget js/LZString "compressToBase64")]
    (if (-> db (store/query []) first :app-state :compress-saved-files?)
      (compress (pr-str db))
      (pr-str db))))

(defn store-db
  [db storage-el]
  (let [comp-db (compress-db db)]
    (set! (.-text storage-el) comp-db)))

(defn- full-html
  []
  (let [content js/document.documentElement.outerHTML]
    (if js/document.doctype
      (str "<!DOCTYPE "
           (when js/document.doctype.name
             js/document.doctype.name)
           (when js/document.doctype.publicId
             (str " PUBLIC \"" js/document.doctype.publicId "\""))
           (when js/document.doctype.systemId
             (str " \"" js/document.doctype.systemId "\""))
           ">\n"
           content)
      content)))

(defn- substitute-time-date
  [file-name]
  (let [dobj (js/Date.)
        date (gstring/format "%04d-%02d-%02d" (.getFullYear dobj) (inc (.getMonth dobj)) (.getDate dobj))
        time (gstring/format "%02d-%02d-%02d" (.getHours dobj) (.getMinutes dobj) (.getSeconds dobj))]
    (-> file-name
        (clojure.string/replace "%d" date)
        (clojure.string/replace "%D" date)
        (clojure.string/replace "%t" time)
        (clojure.string/replace "%T" time))))

(defn- file-name
  [default-name db]
  (let [filename (-> db (store/query []) first :app-state :orgpad-filename)
        orgpad-filename (when filename (substitute-time-date filename))
        p (js/document.location.pathname.lastIndexOf "/")]
    (if orgpad-filename
      orgpad-filename
      (if (not= p -1)
        (js/document.location.pathname.substr (inc p))
        default-name))))

(defn- store-file
  [filename content mime-type]
  (let [link (js/document.createElement "a")]
    (if (not= js/window.Blob js/undefined)
      (let [blob (js/Blob. #js [content] #js {:type mime-type})]
        (.setAttribute link "href" (js/window.URL.createObjectURL blob)))
      (.setAttribute link "href" (str "data:" mime-type ","
                                      (js/window.encodeURIComponent content))))
    (doto link
      (.setAttribute "download" filename)
      (js/document.body.appendChild)
      (.click)
      (js/document.body.removeChild))))

(defn export-html-by-uri
  [db storage-el]
  (store-db db storage-el)
  (let [filename (file-name "orgpad.html" db)]
    (store-file filename (full-html) "text/html")))

(defn save-file-by-uri
  [db]
  (let [filename (.replace (file-name "untitled.orgpad" db) "html" "orgpad")]
    (store-file filename (compress-db db) "text/plain")))

(defn load-orgpad
  [db files]
  (orgpad-db (get files 0)))

(defn download-orgpad-from-url
  [url transact!]
  (ajax/GET url {:handler #(transact! [[:orgpad/load-orgpad [%]]])
                 :error-handler #(js/console.log (str "Error while downloading from " url " " %))
                 :format :text}))

(defn- get-root-traslations
  [db rid]
  (into {}
        (map (fn [[k v]] [k (:translate v)]))
        (store/query db '[:find ?vn ?t
                          :in $ ?r
                          :where
                          [?r :orgpad/props-refs ?p]
                          [?p :orgpad/view-name ?vn]
                          [?p :orgpad/transform ?t]]
                     [rid])))

(defn import-orgpad
  [state files]
  (let [file-state (load-orgpad state files)
        trans (get-root-traslations state root-entity-id)]
    (ot/merge-orgpads state file-state root-entity-id trans)))
