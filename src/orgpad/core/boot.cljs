(ns ^{:doc "Initialization functionality"}
  orgpad.core.boot
  (:require [orgpad.components.registry :as registry]
            [orgpad.cycle.life :as lc]
            [orgpad.core.store :as store]
            [orgpad.core.orgpad :as orgpad]
            [orgpad.parsers.default-unit :as ps]
            [orgpad.config]
            ))

(enable-console-print!)

(defn init [root-el]
  (let [empty-db (orgpad/empty-orgpad-db)]
    (lc/create-cycle empty-db
                     ps/read
                     ps/mutate
                     ps/updated?
                     root-el
                     (-> :orgpad/root-view registry/get-component-info :orgpad/class))
    (.log js/console "ORGPAD 2.0 BOOT.")))

(defn on-js-reload []
  )

(def data-readers {'orgpad/DatomStore store/datom-store-from-reader
                   'orgpad/DatomAtomStore store/datom-atom-store-from-reader})

(doseq [[tag cb] data-readers] (cljs.reader/register-tag-parser! tag cb))
