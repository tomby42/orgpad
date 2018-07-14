(ns orgpad.cycle.utils
  (:require [orgpad.tools.orgpad :as ot]))

(defn build-children-old-node-cache
  [old-node]
  (if old-node
    (persistent!
     (reduce (fn [m n]
               (assoc! m (-> n (aget "value") ot/uid) n))
             (transient {}) (aget old-node "children")))
    {}))
