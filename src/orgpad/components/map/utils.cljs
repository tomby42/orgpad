(ns ^{:doc "Map component utils"}
    orgpad.components.map.utils)

(def mouse-pos (volatile! nil))

(defn set-mouse-pos!
  [ev]
  (vreset! mouse-pos {:mouse-x (.-clientX ev)
                      :mouse-y (.-clientY ev)}))
