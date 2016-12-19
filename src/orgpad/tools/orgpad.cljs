(ns ^{:doc "Orgpad tools"}
  orgpad.tools.orgpad)

(defn- uid
  [unit]
  (-> unit :unit :db/id))

(defn- sort-refs
  [unit]
  (into [] (sort #(compare (uid %1) (uid %2)) (unit :orgpad/refs))))