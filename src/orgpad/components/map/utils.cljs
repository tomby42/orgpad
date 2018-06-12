(ns ^{:doc "Map component utils"}
  orgpad.components.map.utils
  (:require [orgpad.cycle.life :as lc]))

(def mouse-pos (volatile! nil))

(defn set-mouse-pos!
  [ev]
  (vreset! mouse-pos {:mouse-x (.-clientX ev)
                      :mouse-y (.-clientY ev)}))

(defn- parent-id
  [view]
  (-> view :orgpad/refs first :db/id))

(defn- start-change-link-shape
  [unit-tree prop component start-pos end-pos mid-pt t local-state ev]
  (.stopPropagation ev)
  (let [parent-view (aget component "parent-view")]
    (swap! local-state merge {:local-mode :link-shape
                              :selected-link [unit-tree prop parent-view start-pos end-pos mid-pt t]
                              :link-menu-show :maybe
                              :selected-unit nil
                              :mouse-x (if (.-clientX ev) (.-clientX ev) (aget ev "touches" 0 "clientX"))
                              :mouse-y (if (.-clientY ev) (.-clientY ev) (aget ev "touches" 0 "clientY")) })
    (lc/transact! component [[ :orgpad.units/deselect-all {:pid (parent-id parent-view)} ]])))

