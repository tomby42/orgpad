(ns ^{:doc "Map unit component"}
  orgpad.components.map.unit-editor
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.cycle.life :as lc]
            [orgpad.components.registry :as registry]
            [orgpad.components.menu.circle :as mc]
            [orgpad.components.node :as node]
            [orgpad.tools.css :as css]
            [orgpad.tools.js-events :as jev]
            [orgpad.tools.rum :as trum]
            [orgpad.components.graphics.primitives :as g]
            [orgpad.components.menu.color.picker :as cpicker]))

(def ^:private padding 20)
(def ^:private diam (- (* padding 2) 5))

(def ^:private menu-conf {
  :always-open? true
  :init-state true
  :init-rotate -135
  :init-scale 0.5
  :child-init-scale 0.2
  :child-init-rotation -180
  :main-spring-config #js [500 30]
  :child-diam diam
  :main-diam diam
  :offset 0.4
  :child-class "circle-menu-child"
  :main-class "circle-menu-child"
  :final-child-pos-fn mc/final-child-delta-fix-pos
  :child-spring-config #js [800 50]
})

(def ^:private prop-menu-conf {
  :always-open? false
  :init-state true
  :init-rotate -135
  :init-scale 0.5
  :main-spring-config #js [500 30]
  :fly-out-radius 50
  :base-angle 30
  :separation-angle 50
  :child-diam 35
  :child-init-scale 0.2
  :child-init-rotation -180
  :main-diam 40
  :offset 0.4
  :child-class "circle-menu-child"
  :final-child-pos-fn mc/final-child-delta-pos-rot
  :child-spring-config #js [400 28]
})

(defn- compute-children-position
  [prop]
  (let [w (prop :orgpad/unit-width)
        h (prop :orgpad/unit-height)
        bw (prop :orgpad/unit-border-width)
        bw2 (* 2 bw)]
    [{ :dx (- (+ (/ w 2) bw))
       :dy padding }
     { :dx (- (+ w padding bw2))
       :dy padding }
     { :dx (- (+ w padding bw2))
       :dy (- (+ h padding bw2)) }
     { :dx padding
       :dy (- (+ h padding bw2)) }
     ]))

(defn- selected-unit-prop
  [{:keys [unit] :as unit-tree} unit-id prop-id]
  (let [sel-unit
        (->> unit
             :orgpad/refs
             (filter (fn [{:keys [unit]}] (= (unit :db/id) unit-id)))
             first)
        sel-prop
        (->> sel-unit
             :props
             (filter (fn [prop] (and prop (= (prop :db/id) prop-id))))
             first)]
    [sel-unit sel-prop]))

(defn- open-unit
  [component { :keys [unit view path-info] }]
  (let [{ :keys [orgpad/view-name orgpad/view-type] } view
        view-path (path-info :orgpad/view-path)]
    (lc/transact! component [[ :orgpad/root-view-stack { :db/id (unit :db/id)
                                                         :orgpad/view-name view-name
                                                         :orgpad/view-type view-type
                                                         :orgpad/view-path view-path } ]])))

(defn- close-props-menu
  [local-state]
  (js/setTimeout #(swap! local-state merge { :show-props-menu false
                                             :show-color-picker false
                                             :show-border-width false
                                             :show-border-radius false
                                            }) 200))

(defn- toggle-color-picker
  [local-state action]
  (let [{:keys [show-color-picker color-picker-action]} @local-state]
    (swap! local-state merge { :show-color-picker (if (= action color-picker-action) (not show-color-picker) true)
                               :show-border-width false
                               :show-border-radius false
                               :color-picker-action action })))

(defn- toggle-border-width
  [local-state]
  (swap! local-state merge { :show-border-width (not (@local-state :show-border-width))
                             :show-color-picker false
                             :show-border-radius false }))

(defn- toggle-border-radius
  [local-state]
  (swap! local-state merge { :show-border-width false
                             :show-color-picker false
                             :show-border-radius (not (@local-state :show-border-radius)) }))

(defn- render-props-menu
  [unit prop local-state]
  (let [pos (prop :orgpad/unit-position)
        h   (prop :orgpad/unit-width)
        bw  (* 2(prop :orgpad/unit-border-width))]
    (mc/circle-menu
     (merge prop-menu-conf { :center-x (+ (pos 0) padding h bw)
                             :center-y (- (pos 1) padding)
                             :onMouseDown jev/block-propagation
                             :onMouseUp jev/block-propagation })
     [ :i { :title "Properties" :className "fa fa-cogs fa-lg" :onMouseDown #(close-props-menu local-state) } ]
     [ :span { :title "Border color" :onMouseDown #(toggle-color-picker local-state :orgpad.units/map-view-unit-border-color) }
      [ :i { :className "fa fa-square-o" :style { :position "absolute" :top 10 :left 10 } } ]
      [ :i { :className "fa fa-paint-brush" :style { :position "absolute" :top 10 :left 10 } } ] ]
     [ :span { :title "Background color" :onMouseDown #(toggle-color-picker local-state :orgpad.units/map-view-unit-bg-color) }
      [ :i { :className "fa fa-square" :style { :position "absolute" :top 15 :left 10 } } ]
      [ :i { :className "fa fa-paint-brush" :style { :position "absolute" :top 10 :left 10 :color "#030303" } } ] ]
     [ :span { :title "Border width" :onMouseDown #(toggle-border-width local-state) }
      [ :i { :className "fa fa-minus" :style { :position "absolute" :top 20 :left 11 } } ]
      [ :i { :className "fa fa-minus fa-lg" :style { :position "absolute" :top 15 :left 9 } } ]
      [ :i { :className "fa fa-minus fa-2x" :style { :position "absolute" :top 0 :left 5 } } ] ]
     [ :i { :title "Border radius" :className "fa fa-square-o fa-lg" :onMouseDown #(toggle-border-radius local-state) } ]
     )))

(defn- render-color-picker
  [component unit prop parent-view local-state]
  (let [pos (prop :orgpad/unit-position)
        h   (prop :orgpad/unit-width)
        action (@local-state :color-picker-action)
        color (if (= action :orgpad.units/map-view-unit-border-color) (prop :orgpad/unit-border-color) (prop :orgpad/unit-bg-color))]
    [ :div { :style { :position "absolute" :top (- (pos 1) 300) :left (+ (pos 0) h -200) :width 200 :height 200 } }
     (cpicker/color-picker color {} (fn [c]
                                      (lc/transact! component [[ action { :prop prop
                                                                          :parent-view parent-view
                                                                          :unit-tree unit
                                                                          :color c } ]]))) ] ))

(defn- render-slider
  [component unit prop parent-view local-state {:keys [max prop-name action]}]
  [ :input { :type "range" :min 0 :max max :step 1 :value (prop prop-name)
             :onMouseDown #(do
                             (swap! local-state assoc :local-mode :default-mode)
                             (.stopPropagation %))
             :onBlur #(do (.stopPropagation %))
             :onChange (fn [ev]
                         (lc/transact! component [[ action
                                                   { :prop prop
                                                     :parent-view parent-view
                                                     :unit-tree unit
                                                     prop-name (js/parseInt (-> ev .-target .-value)) } ]])) } ])

(defn- render-border-width
  [component unit prop parent-view local-state]
  (let [pos (prop :orgpad/unit-position)
        h   (prop :orgpad/unit-width)]
    [ :div { :style { :position "absolute" :top (- (pos 1) 120) :left (+ (pos 0) h) :width 200 :height 20 } }
     (render-slider component unit prop parent-view local-state { :max 20
                                                                  :prop-name :orgpad/unit-border-width
                                                                  :action :orgpad.units/map-view-unit-border-width }) ]))

(defn- render-border-radius
  [component unit prop parent-view local-state]
  (let [pos (prop :orgpad/unit-position)
        h   (prop :orgpad/unit-width)]
    [ :div { :style { :position "absolute" :top (- (pos 1) 150) :left (+ (pos 0) h) :width 200 :height 20 } }
     (render-slider component unit prop parent-view local-state
                    { :max 50
                      :prop-name :orgpad/unit-corner-x
                      :action :orgpad.units/map-view-unit-border-radius })
     (render-slider component unit prop parent-view local-state
                    { :max 50
                      :prop-name :orgpad/unit-corner-y
                      :action :orgpad.units/map-view-unit-border-radius }) ]))

(rum/defcc unit-editor < lc/parser-type-mixin-context
  [component {:keys [view] :as unit-tree} app-state local-state]
  (let [select-unit (@local-state :selected-unit)]
    (when select-unit
      (let [[old-unit old-prop] select-unit
            [unit prop] (selected-unit-prop unit-tree (-> old-unit :unit :db/id) (old-prop :db/id))]
        (when (and prop unit)
          (let [pos (prop :orgpad/unit-position)
                bw (prop :orgpad/unit-border-width)
                style (merge { :width (+ (prop :orgpad/unit-width) (* 2 bw))
                               :height (+ (prop :orgpad/unit-height) (* 2 bw)) }
                               (css/transform { :translate [(- (pos 0) 2) (- (pos 1) 2)] }))]
            [:div {}
             [ :div { :className "map-view-unit-selected" :style style :key 0 } ]
             (mc/circle-menu
              (merge menu-conf { :center-x (- (pos 0) padding)
                                 :center-y (- (pos 1) padding)
                                 :children-positions (compute-children-position prop)
                                 :onMouseDown jev/block-propagation
                                 :onMouseUp jev/block-propagation })
              [ :i { :title "Move"
                     :className "fa fa-arrows fa-lg"
                     :onMouseDown #(swap! local-state merge { :local-mode :unit-move
                                                              :mouse-x (.-clientX %)
                                                              :mouse-y (.-clientY %) })
                     :onMouseUp #(swap! local-state merge { :local-mode :none }) } ]
              [ :i { :title "Edit"
                     :className "fa fa-pencil-square-o fa-lg"
                     :onMouseUp #(open-unit component unit)
                    } ]
              [ :i { :title "Properties" :className "fa fa-cogs fa-lg"
                     :onMouseUp #(swap! local-state assoc :show-props-menu true) } ]
              [ :i { :title "Resize"
                     :className "fa fa-arrows-alt fa-lg"
                     :onMouseDown #(swap! local-state merge { :local-mode :unit-resize
                                                              :mouse-x (.-clientX %)
                                                              :mouse-y (.-clientY %) })
                     :onMouseUp #(swap! local-state merge { :local-mode :none })} ]
              [ :i { :title "Link" :className "fa fa-link fa-lg"
                     :onMouseDown #(swap! local-state merge { :local-mode :make-link
                                                              :link-start-x (.-clientX %)
                                                              :link-start-y (.-clientY %)
                                                              :mouse-x (.-clientX %)
                                                              :mouse-y (.-clientY %) }) } ]
              )
             (when (= (@local-state :local-mode) :make-link)
               (g/line [(@local-state :link-start-x) (@local-state :link-start-y)]
                       [(@local-state :mouse-x) (@local-state :mouse-y)] {}))
             (when (@local-state :show-props-menu)
               (render-props-menu unit prop local-state))
             (when (@local-state :show-color-picker)
               (render-color-picker component unit prop view local-state))
             (when (@local-state :show-border-width)
               (render-border-width component unit prop view local-state))
             (when (@local-state :show-border-radius)
               (render-border-radius component unit prop view local-state))
             ]
            ))))))
