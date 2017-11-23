(ns ^{:doc "Properties editor component"}
  orgpad.components.map.props-editor
  (:require-macros [orgpad.tools.colls :refer [>-]])
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
            [orgpad.tools.orgpad :as ot]
            [orgpad.tools.orgpad-manipulation :as omt]
            [orgpad.tools.dom :as dom]
            [orgpad.components.graphics.primitives :as g]
            [orgpad.components.menu.color.picker :as cpicker]
            [orgpad.components.menu.direction.picker :as dpicker]
            [goog.string :as gstring]
            [goog.string.format]))

(defn- normalize-range
  [min max val]
  (-> (if (= val "") "0" val)
      js/parseInt
      (js/Math.max min)
      (js/Math.min max)))

(defn- mouse-down-default
  [local-state ev]
  (swap! local-state assoc :local-mode :default-mode)
  (.stopPropagation ev))

(defn- render-slider
  [{:keys [component unit prop parent-view local-state max prop-name action selection]}]
  (let [on-change
        (if (nil? selection)
          (fn [ev]
            (lc/transact! component [[action
                                      {:prop prop
                                       :parent-view parent-view
                                       :unit-tree unit
                                       prop-name (normalize-range 0 max (-> ev .-target .-value)) } ]]))
          (fn [ev]
            (lc/transact! component [[:orgpad.units/map-view-units-change-props
                                      {:action action
                                       :selection selection
                                       :unit-tree unit
                                       :prop-name prop-name
                                       :prop-val (normalize-range 0 max (-> ev .-target .-value)) } ]])))]
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

(def ^:private border-styles
  [ "none" "solid" "dotted" "dashed" "double" "groove" "ridge" "inset" "outset" ])

(defn- render-color-picker
  [{:keys [component unit prop parent-view local-state selection action]}]
  (let [color (if (= action :orgpad.units/map-view-unit-border-color)
                (prop :orgpad/unit-border-color)
                (prop :orgpad/unit-bg-color))
        on-change (if (nil? selection)
                    (fn [c]
                      (lc/transact! component [[action {:prop prop
                                                        :parent-view parent-view
                                                        :unit-tree unit
                                                        :color c}]]))
                    (fn [c]
                      (lc/transact! component [[:orgpad.units/map-view-units-change-props
                                                {:selection selection
                                                 :action action
                                                 :unit-tree unit
                                                 :prop-name :color
                                                 :prop-val c}]])))]
    [ :div.map-view-border-edit {}
     [ :div.center (if (= action :orgpad.units/map-view-unit-border-color)
                     "Border Color"
                     "Background Color") ]
     (cpicker/color-picker color {} on-change) ] ))

(defn- render-border-width
  [{:keys [prop] :as params}]
  [ :div.map-view-border-edit {}
   [:div.center "Border Width"]
   (render-slider (merge params {:max 20
                                 :prop-name :orgpad/unit-border-width
                                 :action :orgpad.units/map-view-unit-border-width })) ])


(defn- render-border-radius
  [{:keys [prop] :as params}]
  [ :div.map-view-border-edit {}
   [ :div.center "Border Radius" ]
   (render-slider (merge params
                         {:max 50
                          :prop-name :orgpad/unit-corner-x
                          :action :orgpad.units/map-view-unit-border-radius }))
   (render-slider (merge params
                         {:max 50
                          :prop-name :orgpad/unit-corner-y
                          :action :orgpad.units/map-view-unit-border-radius })) ])

(defn- render-border-style
  [{:keys [component unit prop parent-view local-state selection]}]
  (let [style (prop :orgpad/unit-border-style)
        on-change (if (nil? selection)
                    (fn [ev]
                      (lc/transact! component
                                    [[:orgpad.units/map-view-unit-border-style
                                      {:prop prop
                                       :parent-view parent-view
                                       :unit-tree unit
                                       :orgpad/unit-border-style (-> ev .-target .-value) } ]]))
                    (fn [ev]
                      (lc/transact! component
                                    [[:orgpad.units/map-view-units-change-props
                                      {:action :orgpad.units/map-view-unit-border-style
                                       :selection selection
                                       :unit-tree unit
                                       :prop-name :orgpad/unit-border-style
                                       :prop-val (-> ev .-target .-value) } ]])))]
    [ :div.-100.map-view-border-edit {}
      [ :div.center "Border Style" ]
     (into
      [ :select.fake-center
       { :onMouseDown (partial mouse-down-default local-state)
         :onBlur jev/stop-propagation
         :onChange on-change } ]
      (map (fn [s]
             [ :option (if (= s style) { :selected true } {}) s ])
           border-styles) ) ] ))

(defn- render-direction-picker [params]
  (dpicker/direction-picker #(js/console.log %) :topright))

;; maji byt informace o otevrenych zalozkach soucasti globalnich parametru orgpadu? davalo by to
;; celkem smysl, aby se stav zachoval pri ulozeni
(rum/defcs props-component < (rum/local false ::open)
  [local-state]
  (let [ open (::open local-state) ]
     [:span
        {:onClick #(swap! open not)}
        "Colors"
        (if (= @open true)
          [:i.fa.fa-chevron-down.fa-lg]
          [:i.fa.fa-chevron-right.fa-lg])]
          
    
  ))
(defn render-node-props-editor
  [params]
  [:div.map-props-toolbar {:key "prop-menu"}
   (render-color-picker (assoc params :action :orgpad.units/map-view-unit-border-color))
   (render-color-picker (assoc params :action :orgpad.units/map-view-unit-bg-color))
   (render-border-width params)
   (render-border-radius params)
   (render-border-style params)
   (render-direction-picker params)
   (props-component)])

(defn- render-link-color-picker
  [{:keys [component unit prop parent-view local-state]}]
  (let [color (prop :orgpad/link-color)]
    [ :div.map-view-border-edit {}
     [ :div.center "Line Color" ]
     (cpicker/color-picker color {} (fn [c]
                                      (lc/transact! component [[ :orgpad.units/map-view-link-color
                                                                { :prop prop
                                                                  :parent-view parent-view
                                                                  :unit-tree unit
                                                                  :color c } ]]))) ] ))

(defn- render-link-width
  [{:keys [component unit prop parent-view local-state]}]
  [ :div.map-view-border-edit {}
   [:div.center "Line Width"]
   (render-slider {:component component :unit unit :prop prop
                   :parent-view parent-view :local-state local-state
                   :max 20
                   :prop-name :orgpad/link-width
                   :action :orgpad.units/map-view-line-width }) ])

(defn- render-link-style
  [{:keys [component unit prop parent-view local-state]}]
  [ :div.map-view-border-edit {}
   [ :div.center "Line style" ]
   (render-slider {:component component :unit unit
                   :prop (assoc prop :orgpad/link-style-1
                                (or (-> prop :orgpad/link-dash (aget 0)) 0))
                   :parent-view parent-view :local-state local-state
                   :max 50
                   :prop-name :orgpad/link-style-1
                   :action :orgpad.units/map-view-link-style })
   (render-slider {:component component :unit unit
                   :prop (assoc prop :orgpad/link-style-2
                                (or (-> prop :orgpad/link-dash (aget 1)) 0))
                   :parent-view parent-view :local-state local-state
                   :max 50
                   :prop-name :orgpad/link-style-2
                   :action :orgpad.units/map-view-link-style }) ])

(defn render-edge-prop-editor
  [params]
  [:div.map-props-toolbar
   (render-link-color-picker params)
   (render-link-width params)
   (render-link-style params)])
