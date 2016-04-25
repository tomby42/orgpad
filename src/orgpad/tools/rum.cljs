(ns ^{:doc "RUM tools"}
  orgpad.tools.rum
  (:require [rum.core :as rum]))

(defn comp->local-state
  [component]
  (-> component rum/state deref :rum/local))
