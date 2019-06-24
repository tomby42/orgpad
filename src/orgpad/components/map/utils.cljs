(ns ^{:doc "Map component utils"}
  orgpad.components.map.utils
  (:require [orgpad.cycle.life :as lc]
            [orgpad.tools.js-events :as jev]
            [orgpad.components.registry :as registry]
            [orgpad.tools.orgpad :as ot]))

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

(defn selected-unit-prop
  [{:keys [unit] :as unit-tree} unit-id prop-id prop-view-type]
  (let [sel-unit
        (->> unit
             :orgpad/refs
             (filter (fn [{:keys [unit]}] (= (unit :db/id) unit-id)))
             first)
        sel-prop
        (->> sel-unit
             :props
             (filter (fn [prop] (and prop (= (prop :db/id) prop-id))))
             first)
        style-type
        (if (= prop-view-type :orgpad.map-view/vertex-props)
          :orgpad.map-view/vertex-props-style
          :orgpad.map-view/link-props-style)
        sel-style
        (->> sel-unit
             :props
             (filter (fn [prop] (and prop
                                     (= (:orgpad/style-name prop) (:orgpad/view-style sel-prop))
                                     (= (:orgpad/view-type prop)
                                        style-type))))
             first)
        default-style (-> :orgpad/map-view registry/get-component-info
                          :orgpad/child-props-default style-type)]
    [sel-unit (merge default-style sel-style sel-prop)]))

(defn get-current-data
  [unit-tree local-state]
  (if (@local-state :selected-unit)
    (let [[old-unit old-prop parent-view] (@local-state :selected-unit)
          [sel-unit-tree prop] (selected-unit-prop unit-tree (ot/uid old-unit)
                                                   (old-prop :db/id) (:orgpad/view-type old-prop))]
      (if (and prop sel-unit-tree)
        [sel-unit-tree prop parent-view true]
        [{} {} {} false]))
    [{} {} {} false]))

(defn swap-link-direction
  [component unit-tree _]
  (lc/transact! component [[:orgpad.units/map-view-link-swap-dir (:unit unit-tree)]]))
