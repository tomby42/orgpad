(ns ^{:doc "Initialization functionality"}
  orgpad.core.boot
  (:require [orgpad.components.registry :as registry]
            [orgpad.cycle.life :as lc]
            [orgpad.core.orgpad :as orgpad]
            [orgpad.parsers.default :as ps]
            [orgpad.config]
            [try.p2]
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

