(ns orgpad.config
  (:require [devtools.core :as devtools]))

(devtools/install!)
(enable-console-print!)

(def ^:dynamic *online-version* false)
(def ^:dynamic *online-debug* true)
