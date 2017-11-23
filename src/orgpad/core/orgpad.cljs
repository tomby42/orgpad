(ns ^{:doc "Core orgpad functionality"}
  orgpad.core.orgpad
  (:require
   [cljs.reader :as reader]
   [datascript.core   :as d]
   [orgpad.core.store :as store]
   [orgpad.tools.colls :as colls]
   [ajax.core :as ajax]))

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
   :orgpad/view-stack  {:db/cardinality :db.cardinality/many}
   :orgpad/view-name   {}
   :orgpad/view-type   {}
   :orgpad/view-path   {}
   :orgpad/transform   {}
   :orgpad/active-unit {}
   :orgpad/unit-position {}
   :orgpad/unit-visibility {}
   :orgpad/unit-width  {}
   :orgpad/unit-height {}
   :orgpad/unit-border-color {}
   :orgpad/unit-bg-color {}
   :orgpad/unit-border-width {}
   :orgpad/unit-corner-x {}
   :orgpad/unit-corner-y {}
   :orgpad/unit-border-style {}
   :orgpad/link-color {}
   :orgpad/link-width {}
   :orgpad/link-dash {}
   :orgpad/link-mid-pt {}
   :orgpad/refs-order  {}
   :orgpad/text        {}
   :orgpad/response    {}
   :orgpad/parameters  {}
   :orgpad/independent {}
   :orgpad/view-style {}
   :orgpad/style-name {}
   })

(defn empty-orgpad-db
  []
  (-> (store/new-datom-atom-store {} (d/empty-db orgpad-db-schema))
      (store/transact [{ :db/id 0,
                         :orgpad/props-refs 1
                         :orgpad/type :orgpad/root-unit }
                       { :db/id 1,
                         :orgpad/type :orgpad/root-unit-view,
                         :orgpad/refs 0 }
                       ] {})
      (store/transact [[:mode] :write] {})))

(defn- update-refs-orders
  [db]
  (let [refs-orders
        (store/query db
                     '[:find ?eid ?o
                       :in $
                       :where
                       [?eid :orgpad/refs-order ?o]])]

    (mapv (fn [[eid o]]
            [:db/add eid :orgpad/refs-order (apply sorted-set o)])
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

(defn- update-db
  [db]
  (let [qry
        (colls/minto []
                     (update-refs-orders db)
                     (unescape-atoms db))]
    (if (empty? qry)
      db
      (store/transact db qry {}))))

(defn orgpad-db
  [data]
  (if (and data (-> data .-length (not= 0)))
    (let [d (aget js/LZString "decompressFromBase64")
          raw-data
          (if (.startsWith data "#orgpad/DatomAtomStore")
            data
            (d data))]
      (update-db (cljs.reader/read-string raw-data)))
    (empty-orgpad-db)))

(defn- compress-db
  [db]
  (let [c (aget js/LZString "compressToBase64")]
    (c (pr-str db))))

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

(defn- file-name
  [defualt-name]
  (let [p (js/document.location.pathname.lastIndexOf "/")]
    (if (not= p -1)
      (js/document.location.pathname.substr (inc p))
      defualt-name)))

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
  (let [filename (file-name "orgpad.html")]
    (store-file filename (full-html) "text/html")))

(defn save-file-by-uri
  [db]
  (let [filename (.replace (file-name "orgpad.orgpad") "html" "orgpad")]
    (store-file filename (compress-db db) "text/plain")))

(defn load-orgpad
  [db files]
  (orgpad-db (get files 0)))

(defn download-orgpad-from-url
  [url transact!]
  (ajax/GET url { :handler #(transact! [[ :orgpad/load-orgpad [%] ]])
                  :error-handler #(js/console.log (str "Error while downloading from " url " " %))
                  :format :text }))
