(ns ^{:doc "Initialization functionality"}
  orgpad.core.boot
  (:require [orgpad.components.registry :as registry]
            [orgpad.cycle.life :as lc]
            [orgpad.core.store :as store]
            [orgpad.core.orgpad :as orgpad]
            [orgpad.parsers.default-unit :as ps]
            [cemerick.url :as url]
            [orgpad.config]
            [devtools.core :as devtools]
            ))

(devtools/install!)
(enable-console-print!)

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
        from (-> u .-query (get "orgpad-from"))]
    (when from
      ((context :parser-mutate) [[ :orgpad/download-orgpad-from-url from ]]))
    (.log js/console "ORGPAD 2.0 BOOT.")))

(defn on-js-reload []
  )

(def data-readers {'orgpad/DatomStore store/datom-store-from-reader
                   'orgpad/DatomAtomStore store/datom-atom-store-from-reader})

(doseq [[tag cb] data-readers] (cljs.reader/register-tag-parser! tag cb))
