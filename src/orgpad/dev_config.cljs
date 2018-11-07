(ns orgpad.dev-config
  (:require [devtools.core :as devtools]
            [orgpad.config :as cfg]))

(devtools/install!)
(enable-console-print!)

(set! cfg/*online-debug* true)
