(ns ^{:doc "Map unit editor component"}
  orgpad.components.map.unit-editor
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
            [orgpad.tools.geom :as geom :refer [-- ++ *c screen->canvas canvas->screen]]
            [orgpad.tools.orgpad :as ot]
            [orgpad.tools.orgpad-manipulation :as omt]
            [orgpad.tools.styles :as styles]
            [orgpad.tools.dom :as dom]
            [orgpad.tools.math :refer [normalize-range]]
            [orgpad.components.graphics.primitives :as g]
            [orgpad.components.graphics.primitives-svg :as sg]
            [orgpad.components.menu.toolbar.component :as tbar]
            [orgpad.components.menu.color.picker :as cpicker]
            [orgpad.components.atomic.atom-editor :as aeditor]
            [goog.string :as gstring]
            [goog.string.format]
            [orgpad.components.map.utils :refer [mouse-pos set-mouse-pos! start-link get-current-data
                                                 selected-unit-prop swap-link-direction]]
            [orgpad.components.input.slider :as slider]
            [orgpad.components.editors.styles :as stedit]

            [orgpad.components.map.node-unit-editor :as ne]
            [orgpad.components.map.link-editor :refer [edge-unit-editor]]
            ))

(defn- update-ref
  [state]
  (let [local-state (-> state :rum/args last)
        new-node (trum/ref-node state "unit-editor-node")]
    (when (and new-node
               (not= (:unit-editor-node @local-state) new-node))
      (swap! local-state assoc :unit-editor-node new-node))))

(rum/defcc unit-editor < lc/parser-type-mixin-context (trum/gen-update-mixin update-ref)
  (rum/local {:selected-view-type :orgpad/atomic-view
              :page-nav-pos [200 0]})
  [component unit-tree app-state local-state]
  (let [select-unit (@local-state :selected-unit)]
    (if (= (:mode app-state) :write)
      [:div
       (if (> (count (get-in app-state [:selections (ot/uid unit-tree)])) 1)
         (ne/nodes-unit-editor component unit-tree app-state local-state)
         (ne/node-unit-editor component unit-tree app-state local-state))
       (edge-unit-editor component unit-tree app-state local-state)]
      (when select-unit
        (ne/node-unit-manipulator component unit-tree app-state local-state)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Static editor on right
;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- render-slider
  [{:keys [component unit prop parent-view local-state max min prop-name action selection]}]
  (let [on-change
        (if (nil? selection)
          (fn [v]
            (lc/transact! component [[action
                                      {:prop prop
                                       :parent-view parent-view
                                       :unit-tree unit
                                       prop-name v}]]))
          (fn [v]
            (lc/transact! component [[:orgpad.units/map-view-units-change-props
                                      {:action action
                                       :selection selection
                                       :unit-tree unit
                                       :prop-name prop-name
                                       :prop-val v}]])))]
    (slider/render-slider {:max max :min min :value (prop prop-name) :on-change on-change})))

(defn- render-color-picker1
  [{:keys [component unit prop parent-view local-state selection action]}]
  (let [color (if (= action :orgpad.units/map-view-unit-border-color)
                (prop :orgpad/unit-border-color)
                (prop :orgpad/unit-bg-color))
        on-change (when color
                    (if (nil? selection)
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
                                                   :prop-val c}]]))))]
    (stedit/frame (if (= action :orgpad.units/map-view-unit-border-color)
                    "Border Color"
                    "Background Color") (cpicker/color-picker color {:key "colorpicker"} on-change))))

(defn- render-width
  [{:keys [prop] :as params}]
  (stedit/frame "Width"
                (render-slider (merge params {:max js/window.innerWidth
                                              :prop-name :orgpad/unit-width
                                              :action :orgpad.units/map-view-unit-set-size}))))

(defn- render-height
  [{:keys [prop] :as params}]
  (stedit/frame "Height"
                (render-slider (merge params {:max js/window.innerHeight
                                              :prop-name :orgpad/unit-height
                                              :action :orgpad.units/map-view-unit-set-size}))))

(defn- render-border-width1
  [{:keys [prop] :as params}]
  (stedit/frame "Border Width"
                (render-slider (merge params {:max 20
                                              :prop-name :orgpad/unit-border-width
                                              :action :orgpad.units/map-view-unit-border-width}))))

(defn- render-border-radius1
  [{:keys [prop] :as params}]
  (stedit/frame "Border Radius"
                (render-slider (merge params
                                      {:max 50
                                       :prop-name :orgpad/unit-corner-x
                                       :action :orgpad.units/map-view-unit-border-radius}))
                (render-slider (merge params
                                      {:max 50
                                       :prop-name :orgpad/unit-corner-y
                                       :action :orgpad.units/map-view-unit-border-radius}))))

(defn- render-border-style1
  [{:keys [component unit prop parent-view local-state selection]}]
  (let [style (prop :orgpad/unit-border-style)
        on-change (if (nil? selection)
                    (fn [val]
                      (lc/transact! component
                                    [[:orgpad.units/map-view-unit-border-style
                                      {:prop prop
                                       :parent-view parent-view
                                       :unit-tree unit
                                       :orgpad/unit-border-style val}]]))
                    (fn [val]
                      (lc/transact! component
                                    [[:orgpad.units/map-view-units-change-props
                                      {:action :orgpad.units/map-view-unit-border-style
                                       :selection selection
                                       :unit-tree unit
                                       :prop-name :orgpad/unit-border-style
                                       :prop-val val}]])))]
    [:div.map-view-border-edit {}
     [:div.center "Border Style"]
     (stedit/render-selection stedit/border-styles style on-change)]))

(defn- render-padding
  [{:keys [prop] :as params}]
  (stedit/frame "Padding"
                (render-slider (merge params {:max 100
                                              :prop-name :orgpad/unit-padding
                                              :action :orgpad.units/map-view-unit-padding}))))

(defn- render-styles-list
  [{:keys [component unit prop parent-view local-state selection]}]
  (let [styles-list (styles/get-sorted-style-list component :orgpad.map-view/vertex-props-style)
        style (:orgpad/view-style prop)
        on-change (if (nil? selection)
                    (fn [val]
                      (lc/transact! component
                                    [[:orgpad.units/map-view-unit-style
                                      {:prop prop
                                       :parent-view parent-view
                                       :unit-tree unit
                                       :orgpad/view-style val}]]))
                    (fn [val]
                      (lc/transact! component
                                    [[:orgpad.units/map-view-units-change-props
                                      {:action :orgpad.units/map-view-unit-style
                                       :selection selection
                                       :unit-tree unit
                                       :prop-name :orgpad/view-style
                                       :prop-val val}]])))]
    [:div.map-view-border-edit {}
     [:div.center "Style"]
     (stedit/render-selection (map :orgpad/style-name styles-list) style on-change)]))

(defn- render-props-menu1
  [params]
  [:div.map-props-toolbar {:key "prop-menu"
                           :onClick jev/stop-propagation
                           :onDoubleClick jev/stop-propagation
                           :onWheel jev/stop-propagation}
   (render-styles-list params)
   (render-color-picker1 (assoc params :action :orgpad.units/map-view-unit-border-color))
   (render-color-picker1 (assoc params :action :orgpad.units/map-view-unit-bg-color))
   (render-width params)
   (render-height params)
   (render-border-width1 params)
   (render-border-radius1 params)
   (render-border-style1 params)
   (render-padding params)])

(defn- node-unit-editor-static
  [component {:keys [view] :as unit-tree} app-state local-state]
  (let [[unit prop parent-view selected?] (get-current-data unit-tree local-state)
        selection (get-in app-state [:selections (ot/uid unit-tree)])]
    (if (> (count selection) 1)
      (let [params {:component component :unit unit-tree :prop prop
                    :parent-view view :local-state local-state
                    :selection selection}]
        (render-props-menu1 params))
      (let [params {:component component :unit unit :prop prop :parent-view view :local-state local-state}]
        [:div {:style {:display (if selected? "block" "none")}}
         (render-props-menu1 params)]))))

(defn- render-link-color-picker1
  [{:keys [component unit prop parent-view local-state]}]
  (let [color (prop :orgpad/link-color)]
    [:div.map-view-border-edit {}
     [:div.center "Line Color"]
     (cpicker/color-picker color {} (fn [c]
                                      (lc/transact! component [[:orgpad.units/map-view-link-color
                                                                {:prop prop
                                                                 :parent-view parent-view
                                                                 :unit-tree unit
                                                                 :color c}]])))]))

(defn- render-link-width1
  [{:keys [component unit prop parent-view local-state]}]
  [:div.map-view-border-edit {}
   [:div.center "Line Width"]
   (render-slider {:component component :unit unit :prop prop
                   :parent-view parent-view :local-state local-state
                   :max 20
                   :prop-name :orgpad/link-width
                   :action :orgpad.units/map-view-line-width})])

(defn- render-link-style1
  [{:keys [component unit prop parent-view local-state]}]
  [:div.map-view-border-edit {}
   [:div.center "Line style"]
   (render-slider {:component component :unit unit
                   :prop (assoc prop :orgpad/link-style-1
                                (or (-> prop :orgpad/link-dash (aget 0)) 0))
                   :parent-view parent-view :local-state local-state
                   :max 50
                   :prop-name :orgpad/link-style-1
                   :action :orgpad.units/map-view-link-style})
   (render-slider {:component component :unit unit
                   :prop (assoc prop :orgpad/link-style-2
                                (or (-> prop :orgpad/link-dash (aget 1)) 0))
                   :parent-view parent-view :local-state local-state
                   :max 50
                   :prop-name :orgpad/link-style-2
                   :action :orgpad.units/map-view-link-style})])

(defn- render-link-styles-list
  [{:keys [component unit prop parent-view local-state]}]
  (let [styles-list (styles/get-sorted-style-list component :orgpad.map-view/link-props-style)
        style (:orgpad/view-style prop)
        on-change (fn [val]
                    (lc/transact! component [[:orgpad.units/map-view-style-link
                                              {:prop prop
                                               :parent-view parent-view
                                               :unit-tree unit
                                               :orgpad/view-style val}]]))]
    [:div.map-view-border-edit {}
     [:div.center "Style"]
     (stedit/render-selection (map :orgpad/style-name styles-list) style on-change)]))

(defn- render-link-arrow-pos
  [{:keys [component unit prop parent-view local-state]}]
  [:div.map-view-border-edit {}
   [:div.center "Arrow position"]
   (render-slider {:component component :unit unit :prop prop
                   :parent-view parent-view :local-state local-state
                   :min 1 :max 100
                   :prop-name :orgpad/link-arrow-pos
                   :action :orgpad.units/map-view-link-arrow-pos})])

(defn- render-link-directions
  [{:keys [component unit prop parent-view local-state]}]
  (let [undirected-style (str "btn" (when (= (:orgpad/link-type prop) :undirected) " active"))
        directed-style (str "btn" (when (= (:orgpad/link-type prop) :directed) " active"))
        bidirected-style (str "btn" (when (= (:orgpad/link-type prop) :bidirected) " active"))]
    [:div.map-view-border-edit {}
     [:div.center "Link direction"]
     [:div.btn-panel
      [:span
       {:class undirected-style
        :title "Undirected link"
        :on-click #(lc/transact! component [[:orgpad.units/map-view-link-type
                                             {:prop prop
                                              :parent-view parent-view
                                              :unit-tree unit
                                              :orgpad/link-type :undirected}]])}
       [:i.far.fa-minus]]

      [:span
       {:class directed-style
        :title "Directed link"
        :on-click #(lc/transact! component [[:orgpad.units/map-view-link-type
                                             {:prop prop
                                              :parent-view parent-view
                                              :unit-tree unit
                                              :orgpad/link-type :directed}]])}
       [:i.far.fa-long-arrow-right]]
      [:span
       {:class bidirected-style
        :title "Directed link both ways"
        :on-click #(lc/transact! component [[:orgpad.units/map-view-link-type
                                             {:prop prop
                                              :parent-view parent-view
                                              :unit-tree unit
                                              :orgpad/link-type :bidirected}]])}
       [:i.far.fa-arrows-h]]
      [:span.fill]
      [:span.btn
       {:title "Flip direction"
        :on-click (partial swap-link-direction component unit)}
       [:i.far.fa-exchange]
       [:span.btn-icon-label "Flip"]]]]))

(defn- render-edge-prop-menu
  [params]
  [:div.map-props-toolbar
   (render-link-styles-list params)
   (render-link-color-picker1 params)
   (render-link-width1 params)
   (render-link-style1 params)
   (render-link-arrow-pos params)
   (render-link-directions params)])

(defn- edge-unit-editor-static
  [component {:keys [unit view] :as unit-tree} app-state local-state]
  (let [select-link (@local-state :selected-link)]
    (when select-link
      (let [[old-unit old-prop _ _ _ mid-pt] select-link
            [unit prop] (selected-unit-prop unit-tree (ot/uid old-unit) (:db/id old-prop) (:orgpad/view-type old-prop))
            params {:component component :unit unit :prop prop :parent-view view :local-state local-state}]
        (render-edge-prop-menu params)))))

(rum/defcc unit-editor-static < lc/parser-type-mixin-context
  [component unit-tree app-state local-state]
  (let [select-unit (@local-state :selected-unit)]
    [:div {:onMouseDown jev/stop-propagation
           :onTouchStart jev/stop-propagation
           :onWheel jev/stop-propagation}
     (node-unit-editor-static component unit-tree app-state local-state)
     (edge-unit-editor-static component unit-tree app-state local-state)]))
