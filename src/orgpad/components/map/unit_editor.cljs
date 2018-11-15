(ns ^{:doc "Map unit component"}
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
            [goog.string :as gstring]
            [goog.string.format]
            [orgpad.components.map.utils :refer [mouse-pos set-mouse-pos! start-link]]
            [orgpad.components.input.slider :as slider]
            [orgpad.components.editors.styles :as stedit]))

(def ^:private edge-menu-conf
  {:always-open? false
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
   :child-spring-config #js [400 28]})

(defn- selected-unit-prop
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
        default-style (-> :orgpad/map-view registry/get-component-info :orgpad/child-props-default style-type)]
    [sel-unit (merge default-style sel-style sel-prop)]))

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

(defn enable-quick-edit
  [local-state]
  (let [react-component (-> @local-state :selected-unit (nth 3) rum/state deref :rum/react-component)]
    (swap! local-state assoc :quick-edit true)
    (trum/force-update react-component)))

(defn- start-unit-move
  [app-state local-state ev]
  (set-mouse-pos! (jev/touch-pos ev))
;;   (js/console.log "start-unit-move" (and (= (:mode app-state) :write)
;;                                          (= (:canvas-mode @local-state) :canvas-create-unit)) app-state @local-state)
  (if (and (= (:mode app-state) :write)
           (= (:canvas-mode @local-state) :canvas-create-unit))
    (start-link local-state ev)
    (swap! local-state merge {:local-mode :unit-move
                              :quick-edit false
                              :pre-quick-edit 0
                              :start-mouse-x (.-clientX (jev/touch-pos ev))
                              :start-mouse-y (.-clientY (jev/touch-pos ev))
                              :mouse-x (.-clientX (jev/touch-pos ev))
                              :mouse-y (.-clientY (jev/touch-pos ev))})))

(defn- start-units-move
  [unit-tree selection local-state ev]
  (set-mouse-pos! (jev/touch-pos ev))
  (swap! local-state merge {:local-mode :units-move
                            :quick-edit false
                            :pre-quick-edit (if (:pre-quick-edit @local-state)
                                              (inc (:pre-quick-edit @local-state))
                                              0)
                            :selected-units [unit-tree selection]
                            :start-mouse-x (.-clientX (jev/touch-pos ev))
                            :start-mouse-y (.-clientY (jev/touch-pos ev))
                            :mouse-x (.-clientX (jev/touch-pos ev))
                            :mouse-y (.-clientY (jev/touch-pos ev))}))

(defn- start-unit-resize
  [local-state mode ev]
  (set-mouse-pos! (jev/touch-pos ev))
  (swap! local-state merge {:local-mode :unit-resize
                            :start-mouse-x (.-clientX (jev/touch-pos ev))
                            :start-mouse-y (.-clientY (jev/touch-pos ev))
                            :mouse-x (.-clientX (jev/touch-pos ev))
                            :mouse-y (.-clientY (jev/touch-pos ev))
                            :resize-mode mode}))

(defn- start-links
  [unit-tree selection local-state ev]
  (set-mouse-pos! (jev/touch-pos ev))
  (start-link local-state ev)
  (swap! local-state merge {:local-mode :make-links
                            :selected-units [unit-tree selection]}))

(def ^:private bb-border [300 300])

(defn compute-bb
  [component unit-tree selection]
  (let [id (ot/uid unit-tree)
        global-cache (lc/get-global-cache component)
        screen-bbox (dom/dom-bb->bb (aget global-cache id "bbox"))
        bbs (map :bb (ot/child-bbs unit-tree selection))
        bb (geom/bbs-bbox bbs)
        transf (-> unit-tree :view :orgpad/transform)
        bb-screen-coord (mapv (partial canvas->screen transf) bb)
        inside? (every? (partial geom/insideBB bb-screen-coord) screen-bbox)]
    (if inside?
      (mapv (partial screen->canvas transf) [(++ (screen-bbox 0) bb-border)
                                             (-- (screen-bbox 1) bb-border)])
      bb)))

(defn- gen-nodes-toolbar
  [{:keys [unit view] :as unit-tree} app-state local-state selection]
  (let [left-toolbar [[{:elem :btn
                        :id "link"
                        :icon "far fa-link"
                        :title "Link"
                        :on-mouse-down #(start-links (:unit-tree %1) (:selection %1) (:local-state %1) %2)
                        :on-touch-start #(start-links (:unit-tree %1) (:selection %1) (:local-state %1) (aget %2 "touches" 0))}]]
        right-toolbar [[{:elem :btn
                         :icon "far fa-trash-alt"
                         :title "Remove"
                         :on-click #(omt/remove-units (:component %1) {:pid (-> %1 :unit-tree ot/uid)
                                                                       :view-name (:orgpad/view-name view)}
                                                      (:selection %1))}]]
        params {:unit-tree    unit-tree
                :unit         unit
                :view         view
                :local-state  local-state
                :selection    selection
                :mode         (:mode app-state)}]
    (tbar/toolbar "uedit-toolbar mini" params left-toolbar right-toolbar)))

(defn- nodes-unit-editor1
  [component {:keys [view] :as unit-tree} app-state local-state parent-view prop]
  (let [selection (get-in app-state [:selections (ot/uid unit-tree)])
        bb (compute-bb component unit-tree selection)
        pos (bb 0)
        [width height] (-- (bb 1) (bb 0))
        style (merge {:width width
                      :height height}
                     (css/transform {:translate [(- (pos 0) 2) (- (pos 1) 2)]}))]
    [:div {:key "node-unit-editor" :ref "unit-editor-node"}
     [:div {:className "map-view-unit-selected"
            :style style
            :key 0
            :onMouseDown (jev/make-block-propagation #(start-units-move unit-tree selection local-state %))
            :onTouchStart (jev/make-block-propagation #(start-units-move unit-tree selection local-state
                                                                         (aget % "touches" 0)))
            ;; :onMouseUp (jev/make-block-propagation #(swap! local-state merge { :local-mode :none }))
}
      (gen-nodes-toolbar unit-tree app-state local-state selection)]

     (when (= (@local-state :local-mode) :make-links)
       (let [tr (parent-view :orgpad/transform)
             bbox (lc/get-global-cache component (ot/uid unit-tree) "bbox")
             ox (.-left bbox)
             oy (.-top bbox)]
         (sg/line (screen->canvas tr [(- (@local-state :link-start-x) ox)
                                      (- (@local-state :link-start-y) oy)])
                  (screen->canvas tr [(- (@local-state :mouse-x) ox)
                                      (- (@local-state :mouse-y) oy)])
                  {:css {:zIndex 2} :svg {:stroke "black"} :key 1})))]))

(defn- node-unit-editor-style
  [prop]
  (let [width (prop :orgpad/unit-width)
        height (prop :orgpad/unit-height)
        pos (-- (prop :orgpad/unit-position) [(/ width 2) (/ height 2)])
        bw (+ (prop :orgpad/unit-padding) (prop :orgpad/unit-border-width))]
    (merge {:width (+ width (* 2 bw))
            :height (+ height (* 2 bw))}
           (css/transform {:translate [(- (pos 0) 2) (- (pos 1) 2)]}))))

(defn- gen-view-toolbar
  [{:keys [unit view] :as unit-tree} view-type]
  (let [view-toolbar (-> view :orgpad/view-type registry/get-component-info :orgpad/uedit-toolbar)]
    (if (and (= view-type :orgpad/map-tuple-view) (not (ot/no-sheets? unit-tree)))
      (let [ac-unit-tree (ot/active-child-tree unit view)
            ac-view-types-roll (tbar/gen-view-types-roll (:view ac-unit-tree) :ac-unit-tree "Current page" "page-views" #(= (:mode %1) :read))
            last-sec (- (count view-toolbar) 1)]
        (update-in view-toolbar [last-sec] conj ac-view-types-roll))
      view-toolbar)))

(defn- gen-toolbar
  [{:keys [unit view] :as unit-tree} parent-tree app-state local-state]
  (let [view-type (ot/view-type unit-tree)
        common-left-toolbar [[{:elem :btn
                               :id "link"
                               :icon "far fa-link"
                               :title "Link"
                               :on-mouse-down #(start-link (:local-state %1) %2)
                               :on-touch-start #(start-link (:local-state %1) (aget %2 "touches" 0))}
                              {:elem :btn
                               :id "edit"
                               :icon "far fa-edit"
                               :title "Edit"
                               :on-click #(omt/open-unit (:component %1) (:unit-tree %1))}]]
        ;toolbar-on-off [{:elem :btn
        ;                 :id "toolbar-on-off"
        ;                 :icon (str "far " (if (:full-toolbar @local-state) "fa-angle-left" "fa-angle-right"))
        ;                 :title (if (:full-toolbar @local-state) "Hide toolbar" "Show toolbar")
        ;                 :on-click #(swap! (:local-state %1) update :full-toolbar not)}]
        view-types-section [(tbar/gen-view-types-roll view :unit-tree "Current" "views" #(= (:mode %1) :read))]
        view-toolbar (gen-view-toolbar unit-tree view-type)
        left-toolbar ;(if (:full-toolbar @local-state)
                       (concat (conj common-left-toolbar view-types-section) view-toolbar); [toolbar-on-off])
                     ;  (conj common-left-toolbar toolbar-on-off))
        right-toolbar [[{:elem :btn
                         :icon "far fa-trash-alt"
                         :title "Remove"
                         :on-click #(omt/remove-unit (:component %1) {:id (-> %1 :unit-tree ot/uid)
                                                                      :view-name (ot/view-name parent-tree)
                                                                      :ctx-unit (ot/uid parent-tree)} (:local-state %1))}]]
        params {:unit-tree    unit-tree
                :unit         unit
                :view         view
                :local-state  local-state
                :mode         (:mode app-state)
                :ac-unit-tree (when (= view-type :orgpad/map-tuple-view) (ot/active-child-tree unit view))
                :ac-view-type (when (= view-type :orgpad/map-tuple-view) (ot/view-type (ot/active-child-tree unit view)))}]
    (tbar/toolbar "uedit-toolbar full" ;(if (:full-toolbar @local-state) "full" "mini"))
                  params left-toolbar right-toolbar)))

(defn- resize-handle
  "Add resize-handle. Class suffixes are top-left, top, top-right, right, bottom-right, bottom, bottom-left, and left."
  [mode local-state]
  (let [class-name (str "resize-handle-" mode)]
    [:span
     {:class class-name :id class-name
      :onMouseDown (jev/make-block-propagation #(start-unit-resize local-state (keyword mode) %))
      :onTouchStart (jev/make-block-propagation #(start-unit-resize local-state (keyword mode) (aget % "touches" 0)))}]))

(defn- node-unit-editor1
  [component {:keys [view] :as unit-tree} app-state local-state]
  (let [[old-unit old-prop parent-view] (@local-state :selected-unit)
        [sel-unit-tree prop] (selected-unit-prop unit-tree (ot/uid old-unit) (old-prop :db/id) (:orgpad/view-type old-prop))]
    (when (and prop sel-unit-tree)
      (if (not= (count (get-in app-state [:selections (ot/uid unit-tree)])) 1)
        (nodes-unit-editor1 component unit-tree app-state local-state parent-view prop)
        (let [style (node-unit-editor-style prop)]
          [:div {:key "node-unit-editor" :ref "unit-editor-node"}
           [:div {:className "map-view-unit-selected"
                  :style style
                  :key 0
                  :onDoubleClick (jev/make-block-propagation #(enable-quick-edit local-state))
                  :onMouseDown (jev/make-block-propagation #(start-unit-move app-state local-state %))
                  :onTouchStart (jev/make-block-propagation #(start-unit-move app-state local-state (aget % "touches" 0)))}
            (for [mode ["top-left" "top" "top-right" "right" "bottom-right" "bottom" "bottom-left" "left"]]
              (resize-handle mode local-state))
            (gen-toolbar sel-unit-tree unit-tree app-state local-state)]
           (when (= (@local-state :local-mode) :make-link)
             (let [tr (parent-view :orgpad/transform)
                   bbox (lc/get-global-cache component (ot/uid unit-tree) "bbox")
                   ox (.-left bbox)
                   oy (.-top bbox)]
               (sg/line (screen->canvas tr [(- (@local-state :link-start-x) ox)
                                            (- (@local-state :link-start-y) oy)])
                        (screen->canvas tr [(- (@local-state :mouse-x) ox)
                                            (- (@local-state :mouse-y) oy)])
                        {:css {:zIndex 2} :svg {:stroke "black"
                                                :stroke-dasharray "4 3"
                                                :stroke-width 3} :key 1})))])))))

(defn- simple-node-unit-editor
  [component {:keys [view] :as unit-tree} app-state local-state]
  (let [[old-unit old-prop parent-view] (@local-state :selected-unit)
        [unit prop] (selected-unit-prop unit-tree (ot/uid old-unit) (old-prop :db/id) (:orgpad/view-type old-prop))]
    (when (and prop unit)
      (let [style (node-unit-editor-style prop)]
        [:div {:key "node-unit-editor" :ref "unit-editor-node"}
         [:div {:className "map-view-unit-selected simple"
                :style style
                :key 0
                :onDoubleClick (jev/make-block-propagation #(enable-quick-edit local-state))
                :onMouseDown (jev/make-block-propagation #(start-unit-move app-state local-state %))
                :onTouchStart (jev/make-block-propagation #(start-unit-move app-state local-state (aget % "touches" 0)))}]]))))

(defn- close-link-menu
  [local-state]
  ;; (js/console.log "close link menu")
  (js/setTimeout
   #(swap! local-state merge {:link-menu-show :none}) 200))

(defn- remove-link
  [component unit local-state]
  (swap! local-state assoc :selected-link nil)
  (lc/transact! component [[:orgpad.units/map-view-link-remove (ot/uid unit)]]))

(defn- swap-link-direction
  [component unit-tree _]
  (lc/transact! component [[:orgpad.units/map-view-link-swap-dir (:unit unit-tree)]]))

(defn- edge-unit-editor
  [component {:keys [view] :as unit-tree} app-state local-state]
  (let [select-link (@local-state :selected-link)]
    (when (and select-link (= (@local-state :link-menu-show) :yes))
      (let [[old-unit old-prop _ _ _ mid-pt] select-link
            [unit prop] (selected-unit-prop unit-tree (ot/uid old-unit) (old-prop :db/id) (:orgpad/view-type old-prop))]
        (when (and prop unit)
          [:div {}
           (mc/circle-menu
            (merge edge-menu-conf {:center-x (mid-pt 0)
                                   :center-y (mid-pt 1)
                                   :onMouseDown jev/block-propagation
                                     ;; :onMouseUp jev/block-propagation
})
            [:i.far.fa-cogs.fa-lg {:title "Properties" :onMouseDown #(close-link-menu local-state)}]
            [:i.far.fa-file-edit.fa-lg
             {:title "Edit"
              :onMouseUp #(omt/open-unit component (assoc-in unit [:view :orgpad/view-type] :orgpad/atomic-view))}]
            [:i.far.fa-exchange.fa-lg {:title "Flip direction" :onMouseDown (partial swap-link-direction component unit)}]
            [:i.far.fa-times.fa-lg {:title "Remove" :onMouseDown #(remove-link component unit local-state)}])])))))

(defn- update-ref
  [state]
  (let [local-state (-> state :rum/args last)
        new-node (trum/ref-node state "unit-editor-node")]
    (when (and new-node
               (not= (:unit-editor-node @local-state) new-node))
      (swap! local-state assoc :unit-editor-node new-node))))

(rum/defcc unit-editor < lc/parser-type-mixin-context (trum/gen-update-mixin update-ref)
  [component unit-tree app-state local-state]
  (let [select-unit (@local-state :selected-unit)]
    (if (= (:mode app-state) :write)
      (if select-unit
        (node-unit-editor1 component unit-tree app-state local-state)
        (edge-unit-editor component unit-tree app-state local-state))
      (when (and select-unit
                 (contains? #{:unit-move} (:local-mode @local-state)))
        (simple-node-unit-editor component unit-tree app-state local-state)))))

(defn- render-color-picker1
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
    (stedit/frame (if (= action :orgpad.units/map-view-unit-border-color)
                    "Border Color"
                    "Background Color") (cpicker/color-picker color {} on-change))))

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
  (let [[old-unit old-prop parent-view] (@local-state :selected-unit)
        [unit prop] (selected-unit-prop unit-tree (ot/uid old-unit) (old-prop :db/id) (:orgpad/view-type old-prop))
        selection (get-in app-state [:selections (ot/uid unit-tree)])]
    (when (and prop unit)
      (if (not= (count selection) 1)
        (let [params {:component component :unit unit-tree :prop prop
                      :parent-view view :local-state local-state
                      :selection selection}]
          (render-props-menu1 params))
        (let [params {:component component :unit unit :prop prop :parent-view view :local-state local-state}]
          (render-props-menu1 params))))))

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
     (if select-unit
       (node-unit-editor-static component unit-tree app-state local-state)
       (edge-unit-editor-static component unit-tree app-state local-state))]))
