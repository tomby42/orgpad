(ns ^{:doc "Map component utils"}
  orgpad.components.map.utils
  (:require [orgpad.cycle.life :as lc]
            [orgpad.tools.js-events :as jev]))

(def mouse-pos (volatile! nil))

(defn set-mouse-pos!
  [ev]
  (vreset! mouse-pos {:mouse-x (.-clientX ev)
                      :mouse-y (.-clientY ev)}))

(defn parent-id
  [view]
  (-> view :orgpad/refs first :db/id))

(defn start-change-link-shape
  [unit-tree prop component start-pos end-pos mid-pt t cyclic? start-size local-state ev]
  (.stopPropagation ev)
  (let [parent-view (aget component "parent-view")]
    (swap! local-state merge {:local-mode :link-shape
                              :selected-link [unit-tree prop parent-view start-pos end-pos mid-pt t
                                              cyclic? start-size]
                              :link-menu-show :maybe
                              :selected-unit nil
                              :mouse-x (if (.-clientX ev) (.-clientX ev) (aget ev "touches" 0 "clientX"))
                              :mouse-y (if (.-clientY ev) (.-clientY ev) (aget ev "touches" 0 "clientY"))})
    (lc/transact! component [[:orgpad.units/deselect-all {:pid (parent-id parent-view)}]])))

(defn start-link
  [local-state ev]
  (swap! local-state merge {:local-mode :make-link
                            :link-start-x (.-clientX (jev/touch-pos ev))
                            :link-start-y (.-clientY (jev/touch-pos ev))
                            :mouse-x (.-clientX (jev/touch-pos ev))
                            :mouse-y (.-clientY (jev/touch-pos ev))}))
