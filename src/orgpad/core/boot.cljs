(ns ^{:doc "Initialization functionality"}
  orgpad.core.boot
  (:require [orgpad.components.registry :as registry]
            [orgpad.config :as ocfg]
            [orgpad.module-config]
            [orgpad.cycle.life :as lc]
            [orgpad.core.store :as store]
            [orgpad.core.orgpad :as orgpad]
            [orgpad.parsers.default-unit :as ps]
            [cemerick.url :as url]
            [orgpad.net.com :as net]))

(enable-console-print!)

(def ^:private *server-url* (if ocfg/*online-debug*
                              "ws://localhost:3000/com"
                              "ws://104.248.29.162:80/com"))

(defn ^:export init [cfg]
  (let [global-cfg (into {} (map (fn [[k v]] [(keyword k) v])) (js->clj cfg))
        init-data (-> global-cfg :storage-el .-text)
        db (orgpad/orgpad-db init-data)
        context (lc/create-cycle db
                                 ps/read
                                 ps/mutate
                                 ps/updated?
                                 (global-cfg :root-el)
                                 (-> :orgpad/root-view registry/get-component-info :orgpad/class)
                                 global-cfg)
        u (url/url (aget js/window "location" "href"))
        from (-> u .-query (get "u"))
        online-id (-> u :query (get "o"))]
    (js/console.log (:query u))
    (when init-data
      ((:parser-mutate context) [[:orgpad/loaded db]]))
    (when from
      ((:parser-mutate context) [[:orgpad/download-orgpad-from-url
                                  ;; from
                                  (str "https://cors-anywhere.herokuapp.com/" from ) ; CORS hack
                                  ]]))
    (when (or online-id ocfg/*online-version*)
      ((:parser-mutate context) [[:orgpad.net/connect-to-server [*server-url* (or online-id "")]]]))
    (.log js/console "ORGPAD BOOT.")))

(defn on-js-reload [])

(def data-readers {'orgpad/DatomStore store/datom-store-from-reader
                   'orgpad/DatomAtomStore store/datom-atom-store-from-reader})

(doseq [[tag cb] data-readers] (cljs.reader/register-tag-parser! tag cb))

(defn- cnz
  [x]
  (-> x sort str))

(extend-type cljs.core.PersistentVector
  IComparable
  (-compare [x y]
    (if (= x y)
      0
      (if (= (count x) (count y))
        (compare (str x) (str y))
        (compare (count x) (count y))))))

(extend-type cljs.core.PersistentArrayMap
  IComparable
  (-compare [x y]
    (if (= x y)
      0
      (if (= (count x) (count y))
        (compare (cnz x) (cnz y))
        (compare (count x) (count y))))))

(extend-type cljs.core.PersistentHashSet
  IComparable
  (-compare [x y]
    (if (= x y)
      0
      (if (= (count x) (count y))
        (compare (cnz x) (cnz y))
        (compare (count x) (count y))))))
