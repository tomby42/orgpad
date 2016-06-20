(ns ^{:doc "Collection tools"}
  orgpad.tools.colls)

(defn minto
  [f & r]
  (reduce into f r))
