(ns ^{:doc "Map unit component"}
  orgpad.components.map.unit
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.cycle.life :as lc]
            [orgpad.components.registry :as registry]
            [orgpad.components.menu.circle :as mc]
            [orgpad.components.node :as node]
            [orgpad.components.map.unit-editor :as uedit]
            [orgpad.tools.css :as css]
            [orgpad.tools.rum :as trum]))

;; TODO configure ??
(def ^:private default-canvas-size 99999)

(defn- select-unit
  [unit-tree prop parent-view local-state]
  (swap! local-state merge { :selected-unit [unit-tree prop parent-view] }))

(defn- props-pred
  [view-name view-type v]
  (and v
       (= (v :orgpad/view-type) view-type)
       (= (v :orgpad/type) :orgpad/unit-view-child)
       (= (v :orgpad/view-name) view-name)))

(defn- get-props
  [props {:keys [orgpad/view-name]}]
  (->> props
       (drop-while #(not (props-pred view-name :orgpad.map-view/props %)))
       first))

(defn- mapped-children
  [{:keys [orgpad/refs]} {:keys [orgpad/view-name]}]
  (let [pred (partial props-pred view-name :orgpad.map-view/props)]
    (filter (fn [u] (->> u :props (some pred))) refs)))

(rum/defcc mapped-unit < rum/static lc/parser-type-mixin-context
  [component {:keys [props unit] :as unit-tree} app-state parent-view local-state]
  (let [prop (get-props props parent-view)
        pos (prop :orgpad/unit-position)
        style (merge { :width (prop :orgpad/unit-width)
                       :height (prop :orgpad/unit-height)
                       :border (str (prop :orgpad/unit-border-width) "px "
                                    (prop :orgpad/unit-border-style) " "
                                    (prop :orgpad/unit-border-color))
                       :borderRadius (str (prop :orgpad/unit-corner-x) "px "
                                          (prop :orgpad/unit-corner-y) "px")
                       :backgroundColor (prop :orgpad/unit-bg-color) }
                     (css/transform { :translate pos })) ]
    (when (= (unit :db/id) (-> local-state deref :selected-unit first :unit :db/id))
      (select-unit unit-tree prop parent-view local-state))
    (html
     [ :div { :style style :className "map-view-child" :key (unit :db/id)
              :onMouseDown (if (= (app-state :mode) :write)
                             #(do
                                (select-unit unit-tree prop parent-view local-state)
                                (.stopPropagation %))
                             #(do
                                (swap! local-state merge { :local-mode :unit-move
                                                           :selected-unit [unit-tree prop parent-view]
                                                           :mouse-x (.-clientX %)
                                                           :mouse-y (.-clientY %) })
                                (.stopPropagation %))) }
       (node/node unit-tree (assoc app-state :mode :read))])))

(defn render-mapped-children-units
  [component {:keys [unit view props] :as unit-tree} app-state local-state]
  (let [style (merge (css/transform (:orgpad/transform view))
                     { :width default-canvas-size
                       :height default-canvas-size })
        m-children (mapped-children unit view)]
    (html
     (into [ :div { :className "map-view-canvas" :style style :key 0 } 
             (when (= (app-state :mode) :write) (uedit/unit-editor unit-tree app-state local-state)) ]
           (map #(mapped-unit % app-state view local-state)) m-children))))
