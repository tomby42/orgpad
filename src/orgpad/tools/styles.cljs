(ns ^{:doc "Style tools"}
  orgpad.tools.styles
  (:require [orgpad.cycle.life :as lc]))

(defn get-sorted-style-list
  [component style-type]
  (sort
    #(compare (:orgpad/style-name %1) (:orgpad/style-name %2))
    (lc/query component :orgpad/styles {:view-type style-type} true)))

