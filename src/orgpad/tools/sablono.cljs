(ns ^{:doc "Sablono tools"}
  orgpad.tools.sablono
  (:require [sablono.core :as html :refer-macros [html]]))

(defn shtml
  [desc]
  (if (seq? desc)
    (if (-> desc first fn?)
      (html (apply (first desc) (rest desc)))
      (html desc))
    (html desc)))
