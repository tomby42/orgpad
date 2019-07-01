(ns orgpad.components.map.node
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [com.rpl.specter :as s :refer-macros [select transform]]
            [orgpad.cycle.life :as lc]
            [orgpad.components.registry :as registry]
            [orgpad.components.node :as node]
            [orgpad.tools.css :as css]
            [orgpad.tools.colls :as colls]
            [orgpad.tools.rum :as trum]
            [orgpad.tools.geom :refer [++ -- *c normalize] :as geom]
            [orgpad.tools.js-events :as jev]
            [orgpad.tools.orgpad :refer [mapped-children mapped-links] :as ot]
            [orgpad.tools.orgpad-manipulation :as omt]
            [orgpad.tools.styles :as styles]
            [orgpad.tools.bezier :as bez]
            [orgpad.tools.math :as math]
            [orgpad.tools.geocache :as geocache]
            [orgpad.tools.func :as func]
            [orgpad.components.graphics.primitives :as g]
            [orgpad.components.graphics.primitives-svg :as sg]
            [orgpad.components.map.utils :refer [mouse-pos set-mouse-pos! start-change-link-shape parent-id
                                                 try-deselect-unit]]))


(defn- select-unit
  [unit-tree prop pcomponent local-state component]
  (swap! local-state merge {:last-selected-unit (:selected-unit @local-state)
                            :selected-unit [unit-tree prop (aget pcomponent "parent-view") component]}))


(defn- try-move-unit
  [component unit-tree app-state prop pcomponent local-state ev]
  (jev/block-propagation ev)
  (let [old-node (:selected-node @local-state)
        new-node (-> component rum/state deref (trum/ref-node "unit-node"))
        parent-view (aget pcomponent "parent-view")]
    (when old-node
      (aset old-node "style" "z-index" "0"))
    (when new-node
      (aset new-node "style" "z-index" "1"))
    (swap! local-state merge {:local-mode :try-unit-move
                              :unit-move-mode :unit
                              :selected-unit [unit-tree prop parent-view component]
                              :selected-link nil
                              :selected-node new-node
                              :quick-edit false
                              :start-mouse-x (.-clientX (jev/touch-pos ev))
                              :start-mouse-y (.-clientY (jev/touch-pos ev))
                              :mouse-x (.-clientX (jev/touch-pos ev))
                              :mouse-y (.-clientY (jev/touch-pos ev))})
    (set-mouse-pos! (jev/touch-pos ev))
    (comment (lc/transact! component [[:orgpad.units/select {:pid (parent-id parent-view)
                                                             :toggle? (.-ctrlKey ev)
                                                             :uid (ot/uid unit-tree)}]]))
    ))

(defn- sheet-indicator
  [active? id]
  [:i {:className (str (if active? "fa" "far") " fa-circle") :id id}])

(defn- insert-sheet-indicators
  [unit-tree color]
  (let [[active-sheet total-sheets] (ot/get-sheet-number unit-tree)]
    (when (> total-sheets 1)
      [:div.tuple-page-indicator {:style {:color color}}
       (map
        #(sheet-indicator (= % (dec active-sheet)) (str "circle-" %))
        (range total-sheets))])))

(rum/defcc map-unit < trum/istatic lc/parser-type-mixin-context
  [component {:keys [props unit] :as unit-tree} app-state pcomponent view-name pid local-state]
  (try
    (let [prop (ot/get-props-view-child-styled props view-name pid
                                               :orgpad.map-view/vertex-props
                                               :orgpad.map-view/vertex-props-style
                                               :orgpad/map-view)
          pos (-- (prop :orgpad/unit-position)
                  [(/ (prop :orgpad/unit-width) 2) (/ (prop :orgpad/unit-height) 2)])
          selections (get-in app-state [:selections pid])
          selected? (= (:db/id unit) (first selections))
          border-color (-> prop :orgpad/unit-border-color css/format-color)
          style-pos (css/transform {:translate pos})
          style (merge (styles/prop->css prop)
                       (when selected?
                         {:zIndex 1}))]
      (when selected?
        (select-unit unit-tree prop pcomponent local-state component))
      (html
       [:div {:id (str "unit-" pid "-" (:db/id unit))
              :className "map-view-child-container"
              :style style-pos
              :ref "unit-node"}
        (when (contains? selections (:db/id unit))
          [:div {:className "map-view-child"
                 :style (-> style
                            (update :width + (* 2 (:borderWidth style)))
                            (update :height + (* 2 (:borderWidth style)))
                            (assoc :top -2 :left -2 :borderWidth 2
                                   :backgroundColor "black"
                                   :borderColor "black"))}])
        [:div
         (if (= (app-state :mode) :write)
           {:style style :className "map-view-child" :key (unit :db/id)
            :onMouseDown #(try-move-unit component unit-tree app-state prop pcomponent local-state %)
            :onTouchStart #(try-move-unit component unit-tree app-state prop pcomponent local-state %)
            :onMouseUp (partial try-deselect-unit component pid (:db/id unit) local-state)
            :onWheel jev/stop-propagation}
           {:style style :className "map-view-child" :key (unit :db/id)
            :onMouseDown #(try-move-unit component unit-tree app-state prop pcomponent local-state %)
            :onTouchStart #(try-move-unit component unit-tree app-state prop pcomponent local-state %)
            :onMouseUp (partial try-deselect-unit component pid (:db/id unit) local-state)
            :onWheel jev/stop-propagation})
         (node/node unit-tree
                    (assoc app-state
                           :mode
                           :read))
         (if (= (app-state :mode) :write)
           (when-not (and selected? (:quick-edit @local-state))
             [:div.map-view-child.hat
              {:style {:top 0
                       :width (prop :orgpad/unit-width)
                       :height (prop :orgpad/unit-height)}
               :onMouseDown #(try-move-unit component unit-tree app-state prop pcomponent local-state %)}])
           nil)
         (when (= (ot/view-type unit-tree) :orgpad/map-tuple-view)
           (insert-sheet-indicators unit-tree border-color))
]]))
    (catch :default e
      (js/console.log "Unit render error" e)
      nil)))
