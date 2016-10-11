(ns ^{:doc "JS events tools"}
  orgpad.tools.js-events)

(defn block-propagation
  [ev]
  (doto ev
    .preventDefault
    .stopPropagation))

(defn stop-propagation
  [ev]
  (doto ev
    .stopPropagation))

(defn- touch-pos
  [ev]
  (if (.-clientX ev)
    ev
    (aget ev "touches" 0)))
