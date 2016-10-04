(ns ^{:doc "Map unit component"}
  orgpad.components.map.unit-editor
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.cycle.life :as lc]
            [orgpad.components.registry :as registry]
            [orgpad.components.menu.circle.component :as mc]
            [orgpad.components.node :as node]
            [orgpad.tools.css :as css]
            [orgpad.tools.js-events :as jev]
            [orgpad.tools.rum :as trum]
            [orgpad.tools.geom :as geom]
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

(def ^:private edge-menu-conf {
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
       :dy (- (+ h (* 1.2 padding) bw2)) }
     { :dx padding
       :dy (- (+ h (* 1.2 padding) bw2)) }
     { :dx (- (+ (/ w 2) bw))
       :dy (- (+ h (* 1.2 padding) bw2)) }
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

(def ^:private closed-editors { :show-color-picker false
                                :show-border-width false
                                :show-border-radius false
                                :show-border-style false })

(defn- close-props-menu
  [local-state]
  (js/setTimeout #(swap! local-state merge closed-editors
                         { :show-props-menu false }) 200))

(defn- toggle-color-picker
  [local-state action]
  (let [{:keys [show-color-picker color-picker-action]} @local-state]
    (swap! local-state merge closed-editors
           { :show-color-picker (if (= action color-picker-action) (not show-color-picker) true)
             :color-picker-action action })))

(defn- toggle-border-editor
  [local-state type]
  (swap! local-state merge closed-editors
         { type (not (@local-state type)) }))

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
     [ :span { :title "Border width" :onMouseDown #(toggle-border-editor local-state :show-border-width) }
      [ :i { :className "fa fa-minus" :style { :position "absolute" :top 20 :left 11 } } ]
      [ :i { :className "fa fa-minus fa-lg" :style { :position "absolute" :top 15 :left 9 } } ]
      [ :i { :className "fa fa-minus fa-2x" :style { :position "absolute" :top 0 :left 5 } } ] ]
     [ :i { :title "Border radius" :className "fa fa-square-o fa-lg" :onMouseDown #(toggle-border-editor local-state :show-border-radius) } ]
     [ :span { :title "Border style" :onMouseDown #(toggle-border-editor local-state :show-border-style) }
      [ :i.fa.fa-square-o.fa-lg { :style { :position "absolute" :left 10 :top 10 } } ]
      [ :i.fa.fa-tint { :style { :position "absolute" } } ] ] )))

(defn- render-color-picker
  [component unit prop parent-view local-state]
  (let [pos (prop :orgpad/unit-position)
        h   (prop :orgpad/unit-width)
        action (@local-state :color-picker-action)
        color (if (= action :orgpad.units/map-view-unit-border-color) (prop :orgpad/unit-border-color) (prop :orgpad/unit-bg-color))]
    [ :div.map-view-border-edit { :style { :width 210 :position "absolute" :top (- (pos 1) 300) :left (+ (pos 0) h -235)  } }
     [ :div.center (if (= action :orgpad.units/map-view-unit-border-color)
                     "Border Color"
                     "Background Color") ]
     (cpicker/color-picker color {} (fn [c]
                                      (lc/transact! component [[ action { :prop prop
                                                                          :parent-view parent-view
                                                                          :unit-tree unit
                                                                          :color c } ]]))) ] ))

(defn- mouse-down-default
  [local-state ev]
  (swap! local-state assoc :local-mode :default-mode)
  (.stopPropagation ev))

(defn- normalize-range
  [min max val]
  (-> (if (= val "") "0" val)
      js/parseInt
      (js/Math.max min)
      (js/Math.min max)))

(defn- render-slider
  [component unit prop parent-view local-state {:keys [max prop-name action]}]
  (letfn [(on-change [ev]
            (lc/transact! component [[ action
                                      { :prop prop
                                        :parent-view parent-view
                                        :unit-tree unit
                                        prop-name (normalize-range 0 max (-> ev .-target .-value)) } ]]))]
    [ :div.slider
     [ :input { :type "range" :min 0 :max max :step 1 :value (prop prop-name)
                :onMouseDown (partial mouse-down-default local-state)
                :onBlur jev/stop-propagation
                :onChange on-change } ]
     [ :input.val { :type "text" :value (prop prop-name)
                    :onBlur jev/stop-propagation
                    :onMouseDown jev/stop-propagation
                    :onChange on-change
 } ] ] ) )

(defn- render-border-width
  [component unit prop parent-view local-state]
  (let [pos (prop :orgpad/unit-position)
        h   (prop :orgpad/unit-width)]
    [ :div.map-view-border-edit { :style { :position "absolute" :top (- (pos 1) 170) :left (+ (pos 0) h) } }
     [:div.center "Border Width"]
     (render-slider component unit prop parent-view local-state { :max 20
                                                                  :prop-name :orgpad/unit-border-width
                                                                  :action :orgpad.units/map-view-unit-border-width }) ]))

(defn- render-border-radius
  [component unit prop parent-view local-state]
  (let [pos (prop :orgpad/unit-position)
        h   (prop :orgpad/unit-width)]
    [ :div.map-view-border-edit { :style { :position "absolute" :top (- (pos 1) 210) :left (+ (pos 0) h)  } }
     [ :div.center "Border Radius" ]
     (render-slider component unit prop parent-view local-state
                    { :max 50
                      :prop-name :orgpad/unit-corner-x
                      :action :orgpad.units/map-view-unit-border-radius })
     (render-slider component unit prop parent-view local-state
                    { :max 50
                      :prop-name :orgpad/unit-corner-y
                      :action :orgpad.units/map-view-unit-border-radius }) ]))

(def ^:private border-styles
  [ "none" "solid" "dotted" "dashed" "double" "groove" "ridge" "inset" "outset" ])

(defn- render-border-style
  [component unit prop parent-view local-state]
  (let [pos (prop :orgpad/unit-position)
        h   (prop :orgpad/unit-width)
        style (prop :orgpad/unit-border-style)]
    [ :div.-100.map-view-border-edit { :style { :width 100 :position "absolute" :top (- (pos 1) 155) :left (+ (pos 0) h) } }
      [ :div.center "Border Style" ]
     (into
      [ :select.fake-center
       { :onMouseDown (partial mouse-down-default local-state)
         :onBlur jev/stop-propagation
         :onChange (fn [ev]
                     (lc/transact! component
                                   [[ :orgpad.units/map-view-unit-border-style
                                     { :prop prop
                                       :parent-view parent-view
                                       :unit-tree unit
                                       :orgpad/unit-border-style (-> ev .-target .-value) } ]]))
                 } ]
      (map (fn [s]
             [ :option (if (= s style) { :selected true } {}) s ])
           border-styles) ) ] ))

(defn- remove-unit
  [component id]
  (lc/transact! component [[ :orgpad.units/remove-unit
                             id ]]))

(def ^:private prop-editors
  { :show-color-picker render-color-picker
    :show-border-width render-border-width
    :show-border-radius render-border-radius
    :show-border-style render-border-style })

(defn- node-unit-editor
  [component {:keys [view] :as unit-tree} app-state local-state]
  (let [[old-unit old-prop parent-view] (@local-state :selected-unit)
        [unit prop] (selected-unit-prop unit-tree (-> old-unit :unit :db/id) (old-prop :db/id))]
    (when (and prop unit)
      (let [pos (prop :orgpad/unit-position)
            bw (prop :orgpad/unit-border-width)
            style (merge { :width (+ (prop :orgpad/unit-width) (* 2 bw))
                           :height (+ (prop :orgpad/unit-height) (* 2 bw)) }
                         (css/transform { :translate [(- (pos 0) 2) (- (pos 1) 2)] }))]
        (into
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
           [ :i { :title "Remove" :className "fa fa-remove fa-lg"
                  :onMouseDown #(remove-unit component (-> unit :unit :db/id))  } ]
           )
          (when (= (@local-state :local-mode) :make-link)
            (let [tr (parent-view :orgpad/transform)]
              (g/line (geom/screen->canvas tr [(@local-state :link-start-x) (@local-state :link-start-y)])
                      (geom/screen->canvas tr [(@local-state :mouse-x) (@local-state :mouse-y)]) {})))
          (when (@local-state :show-props-menu)
            (render-props-menu unit prop local-state))]

         (map (fn [[key render-fn]]
                (when (@local-state key)
                  (render-fn component unit prop view local-state))) prop-editors)
         )))))

(def ^:private link-closed-editors { :show-link-color-picker false
                                     :show-link-width false
                                     :show-link-style false })

(defn- close-link-menu
  [local-state]
  (js/setTimeout
   #(swap! local-state merge { :link-menu-show :none } link-closed-editors) 200))

(defn- render-link-color-picker
  [component unit prop parent-view local-state mid-pt]
  (let [color (prop :orgpad/link-color)]
    [ :div.map-view-border-edit { :style { :width 210 :position "absolute" :top (- (mid-pt 1) 300) :left (- (mid-pt 0) 235) } }
     [ :div.center "Line Color" ]
     (cpicker/color-picker color {} (fn [c]
                                      (lc/transact! component [[ :orgpad.units/map-view-link-color
                                                                { :prop prop
                                                                  :parent-view parent-view
                                                                  :unit-tree unit
                                                                  :color c } ]]))) ] ))

(defn- render-link-width
  [component unit prop parent-view local-state mid-pt]
  [ :div.map-view-border-edit { :style { :position "absolute" :top (- (mid-pt 1) 170) :left (mid-pt 0) } }
   [:div.center "Line Width"]
   (render-slider component unit prop parent-view local-state { :max 20
                                                                :prop-name :orgpad/link-width
                                                                :action :orgpad.units/map-view-line-width }) ])

(defn- render-link-style
  [component unit prop parent-view local-state mid-pt]
  [ :div.map-view-border-edit { :style { :position "absolute" :top (- (mid-pt 1) 210) :left (mid-pt 0) } }
   [ :div.center "Line style" ]
   (render-slider component unit (assoc prop :orgpad/link-style-1
                                        (or (-> prop :orgpad/link-dash (aget 0)) 0)) parent-view local-state
                  { :max 50
                    :prop-name :orgpad/link-style-1
                    :action :orgpad.units/map-view-link-style })
   (render-slider component unit (assoc prop :orgpad/link-style-2
                                        (or (-> prop :orgpad/link-dash (aget 1)) 0)) parent-view local-state
                  { :max 50
                    :prop-name :orgpad/link-style-2
                    :action :orgpad.units/map-view-link-style }) ])

(defn- remove-link
  [component unit]
  (lc/transact! component [[ :orgpad.units/map-view-link-remove (-> unit :unit :db/id) ]]))


(def ^:private link-prop-editors
  { :show-link-color-picker render-link-color-picker
    :show-link-width render-link-width
    :show-link-style render-link-style })

(defn- toggle-link-editor
  [local-state type]
  (swap! local-state merge link-closed-editors
         { type (not (@local-state type)) }))

(defn- edge-unit-editor
  [component {:keys [view] :as unit-tree} app-state local-state]
  (let [select-link (@local-state :selected-link)]
    (when (and select-link (= (@local-state :link-menu-show) :yes))
      (let [[old-unit old-prop _ _ _ mid-pt] select-link
            [unit prop] (selected-unit-prop unit-tree (-> old-unit :unit :db/id) (old-prop :db/id))]
        (when (and prop unit)
          (into
           [:div {}
            (mc/circle-menu
             (merge edge-menu-conf { :center-x (mid-pt 0)
                                     :center-y (mid-pt 1)
                                     :onMouseDown jev/block-propagation
                                     ;; :onMouseUp jev/block-propagation
                                    })
             [ :i.fa.fa-cogs.fa-lg { :title "Properties" :onMouseDown #(close-link-menu local-state) } ]
             [ :span { :title "Line Color" :onMouseDown #(toggle-link-editor local-state :show-link-color-picker) }
              [ :i.fa.fa-minus { :style { :position "absolute" :top 20 } } ]
              [ :i.fa.fa-paint-brush]
              ]
             [ :span { :title "Line Width" :onMouseDown #(toggle-link-editor local-state :show-link-width) }
              [ :i { :className "fa fa-minus" :style { :position "absolute" :top 20 :left 11 } } ]
              [ :i { :className "fa fa-minus fa-lg" :style { :position "absolute" :top 15 :left 9 } } ]
              [ :i { :className "fa fa-minus fa-2x" :style { :position "absolute" :top 0 :left 5 } } ] ]
             [ :span { :title "Line Style" :onMouseDown #(toggle-link-editor local-state :show-link-style) }
              [ :i.fa.fa-minus. { :style { :position "absolute" :top 19 } } ]
              [ :i.fa.fa-tint {} ]
              ]
             [ :i.fa.fa-remove.fa-lg { :title "Remove" :onMouseDown #(remove-link component unit) } ]
           )]

           (map (fn [[key render-fn]]
                  (when (@local-state key)
                    (render-fn component unit prop view local-state mid-pt))) link-prop-editors)
      ))))))

(rum/defcc unit-editor < lc/parser-type-mixin-context
  [component {:keys [view] :as unit-tree} app-state local-state]
  (let [select-unit (@local-state :selected-unit)]
    (if select-unit
      (node-unit-editor component unit-tree app-state local-state)
      (edge-unit-editor component unit-tree app-state local-state))))
