(ns ^{:doc "Math tools"}
  orgpad.tools.math
  (:require [rum.core :as rum]))

(def pi (.-PI js/Math))
(def cos (.-cos js/Math))
(def sin (.-sin js/Math))

(defn deg->rads
  [deg]
  (-> deg (* pi) (/ 180)))
