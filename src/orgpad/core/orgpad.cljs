(ns ^{:doc "Core orgpad functionality"}
  orgpad.core.orgpad
  (:require
   [cljs.reader :as reader]
   [datascript.core   :as d]
   [orgpad.core.store :as store]))

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
   :orgpad/view-name   {}
   :orgpad/view-names  {:db/cardinality :db.cardinality/many}
   :orgpad/view-type   {}
   :orgpad/view-types  {:db/cardinality :db.cardinality/many}
   :orgpad/view-path   {}
   :orgpad/view-paths  {:db/cardinality :db.cardinality/many}
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
                       ])
      (store/transact [[:mode] :write])))

(defn orgpad-db
  [data]
  (if (and data (-> data .-length (not= 0)))
    (let [raw-data
          (if (.startsWith data "#orgpad/DatomAtomStore")
            data
            (js/LZString.decompressFromBase64 data))]
      (cljs.reader/read-string raw-data))
    (empty-orgpad-db)))

(defn store-db
  [db storage-el]
  (let [comp-db (js/LZString.compressToBase64 (pr-str db))]
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

(defn save-file-by-uri
  [db storage-el]
  (store-db db storage-el)
  (let [p (js/document.location.pathname.lastIndexOf "/")
        filename (if (not= p -1)
                   (js/document.location.pathname.substr (inc p))
                   "orgpad.html")
        link (js/document.createElement "a")
        content (full-html)]
    (if (not= js/window.Blob js/undefined)
      (let [blob (js/Blob. #js [content] #js {:type "text/html"})]
        (.setAttribute link "href" (js/window.URL.createObjectURL blob)))
      (.setAttribute link "href" (str "data:text/html,"
                                      (js/window.encodeURIComponent content))))
    (doto link
      (.setAttribute "download" filename)
      (js/document.body.appendChild)
      (.click)
      (js/document.body.removeChild))))
