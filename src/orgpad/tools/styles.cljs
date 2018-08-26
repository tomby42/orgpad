(ns ^{:doc "Style tools"}
  orgpad.tools.styles
  (:require [orgpad.cycle.life :as lc]
            [orgpad.tools.css :as css]))

(defn get-sorted-style-list
  [component style-type]
  (sort
    #(compare (:orgpad/style-name %1) (:orgpad/style-name %2))
    (lc/query component :orgpad/styles {:view-type style-type} true)))

(defn prop->css
  [prop]
 {:width (prop :orgpad/unit-width)
  :height (prop :orgpad/unit-height)
  :borderWidth (prop :orgpad/unit-border-width)
  :borderStyle (prop :orgpad/unit-border-style)
  :borderColor (-> prop :orgpad/unit-border-color css/format-color)
  :borderRadius (str (prop :orgpad/unit-corner-x) "px "
                     (prop :orgpad/unit-corner-y) "px")
  :backgroundColor (-> prop :orgpad/unit-bg-color css/format-color)
  :padding (prop :orgpad/unit-padding)})
