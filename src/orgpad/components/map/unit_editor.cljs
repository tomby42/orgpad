(ns ^{:doc "Map unit component"}
  orgpad.components.map.unit-editor
  (:require-macros [orgpad.tools.colls :refer [>-]])
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.cycle.life :as lc]
            [orgpad.components.registry :as registry]
            [orgpad.components.menu.circle.component :as mc]
            [orgpad.components.node :as node]
			[orgpad.components.map.toolbar :as tbar]
            [orgpad.tools.css :as css]
            [orgpad.tools.js-events :as jev]
            [orgpad.tools.rum :as trum]
            [orgpad.tools.geom :as geom]
            [orgpad.tools.orgpad :as ot]
            [orgpad.tools.orgpad-manipulation :as omt]
            [orgpad.tools.dom :as dom]
            [orgpad.components.graphics.primitives :as g]
            [orgpad.components.menu.color.picker :as cpicker]
            [goog.string :as gstring]
            [goog.string.format]))

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

(defn- remove-units
  [component pid selection]
  (lc/transact! component [[:orgpad.units/remove-units
                            [pid selection]]]))

(defn enable-quick-edit
  [local-state]
  (let [react-component (-> @local-state :selected-unit (nth 3) rum/state deref :rum/react-component)]
    (swap! local-state assoc :quick-edit true)
    (trum/force-update react-component)))

(defn- start-unit-move
  [local-state ev]
  (swap! local-state merge { :local-mode :unit-move
                             :quick-edit false
                             :pre-quick-edit 0
                             :start-mouse-x (.-clientX (jev/touch-pos ev))
                             :start-mouse-y (.-clientY (jev/touch-pos ev))
                             :mouse-x (.-clientX (jev/touch-pos ev))
                             :mouse-y (.-clientY (jev/touch-pos ev)) }))

(defn- start-units-move
  [unit-tree selection local-state ev]
  (swap! local-state merge { :local-mode :units-move
                             :local-move false
                             :quick-edit false
                             :pre-quick-edit (if (:pre-quick-edit @local-state)
                                               (inc (:pre-quick-edit @local-state))
                                               0)
                             :selected-units [unit-tree selection]
                             :start-mouse-x (.-clientX (jev/touch-pos ev))
                             :start-mouse-y (.-clientY (jev/touch-pos ev))
                             :mouse-x (.-clientX (jev/touch-pos ev))
                             :mouse-y (.-clientY (jev/touch-pos ev)) }))


(defn- start-unit-resize
  [local-state ev]
  (swap! local-state merge { :local-mode :unit-resize
                             :local-move false
                             :start-mouse-x (.-clientX (jev/touch-pos ev))
                             :start-mouse-y (.-clientY (jev/touch-pos ev))
                             :mouse-x (.-clientX (jev/touch-pos ev))
                             :mouse-y (.-clientY (jev/touch-pos ev)) }))

(defn- start-links
  [unit-tree selection local-state ev]
  (omt/start-link local-state ev)
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
        bb-screen-coord (mapv (partial geom/canvas->screen transf) bb)
        inside? (every? (partial geom/insideBB bb-screen-coord) screen-bbox)]
    (if inside?
      (mapv (partial geom/screen->canvas transf) [(geom/++ (screen-bbox 0) bb-border)
                                                  (geom/-- (screen-bbox 1) bb-border)])
      bb)))

(defn- nodes-unit-editor1
  [component {:keys [view] :as unit-tree} app-state local-state parent-view prop]
  (let [selection (get-in app-state [:selections (ot/uid unit-tree)])
        bb (compute-bb component unit-tree selection)
        pos (bb 0)
        [width height] (geom/-- (bb 1) (bb 0))
        style (merge {:width width
                      :height height}
                     (css/transform { :translate [(- (pos 0) 2) (- (pos 1) 2)] }))]
    [:div {:key "node-unit-editor" :ref "unit-editor-node"}
     [:div {:className "map-view-unit-selected"
            :style style
            :key 0
            :onMouseDown (jev/make-block-propagation #(start-units-move unit-tree selection local-state %))
            :onTouchStart (jev/make-block-propagation #(start-units-move unit-tree selection local-state
                                                                         (aget % "touches" 0)))
            ;; :onMouseUp (jev/make-block-propagation #(swap! local-state merge { :local-mode :none }))
            }
        [:span.toolbar
         [:span.lft-btn
          {:title "Link"
           :onMouseDown (jev/make-block-propagation #(start-links unit-tree selection local-state %))
           :onTouchStart (jev/make-block-propagation #(start-links unit-tree selection local-state (aget % "touches" 0)))}
          [:i.fa.fa-link.fa-lg]]

         [:span.rt-btn
          {:title "Remove"
           :onMouseDown #(remove-units component (ot/uid unit-tree) selection)}
          [:i.fa.fa-remove.fa-lg]]]]

     (when (= (@local-state :local-mode) :make-links)
       (let [tr (parent-view :orgpad/transform)]
         (g/line (geom/screen->canvas tr [(@local-state :link-start-x) (@local-state :link-start-y)])
                 (geom/screen->canvas tr [(@local-state :mouse-x) (@local-state :mouse-y)])
                 {:css {:zIndex 2}})))]))

(defn- node-unit-editor-style
  [prop]
  (let [pos (prop :orgpad/unit-position)
        width (prop :orgpad/unit-width)
	  	  height (prop :orgpad/unit-height)
			  bw (prop :orgpad/unit-border-width)]
  (merge { :width (+ width (* 2 bw))
           :height (+ height (* 2 bw)) }
           (css/transform { :translate [(- (pos 0) 2) (- (pos 1) 2)] }))))

(defn- node-unit-editor1
  [component {:keys [view] :as unit-tree} app-state local-state]
  (let [[old-unit old-prop parent-view] (@local-state :selected-unit)
        [sel-unit-tree prop] (selected-unit-prop unit-tree (ot/uid old-unit) (old-prop :db/id))]
    (when (and prop sel-unit-tree)
      (if (not= (count (get-in app-state [:selections (ot/uid unit-tree)])) 1)
        (nodes-unit-editor1 component unit-tree app-state local-state parent-view prop)
        (let [style (node-unit-editor-style prop)]
          [:div {:key "node-unit-editor" :ref "unit-editor-node"}
           [:div {:className "map-view-unit-selected"
                  :style style
                  :key 0
                  :onDoubleClick (jev/make-block-propagation #(enable-quick-edit local-state))
                  :onMouseDown (jev/make-block-propagation #(start-unit-move local-state %))
                  :onTouchStart (jev/make-block-propagation #(start-unit-move local-state (aget % "touches" 0)))
                  }
           ; add other resize directions
           [:span.resize-handle-corner {:onMouseDown (jev/make-block-propagation #(start-unit-resize local-state %))
                                 :onTouchStart (jev/make-block-propagation #(start-unit-resize local-state (aget % "touches" 0)))
                                 }]
           [:span.resize-handle-bottom {:onMouseDown (jev/make-block-propagation #(start-unit-resize local-state %))
                                 :onTouchStart (jev/make-block-propagation #(start-unit-resize local-state (aget % "touches" 0)))
                                 }]
           (tbar/render-unit-editor-toolbar component sel-unit-tree app-state local-state)]
           (when (= (@local-state :local-mode) :make-link)
             (let [tr (parent-view :orgpad/transform)]
               (g/line (geom/screen->canvas tr [(@local-state :link-start-x) (@local-state :link-start-y)])
                       (geom/screen->canvas tr [(@local-state :mouse-x) (@local-state :mouse-y)])
                       {:css {:zIndex 2} :key 1})))])))))

(defn- simple-node-unit-editor
  [component {:keys [view] :as unit-tree} app-state local-state]
  (let [[old-unit old-prop parent-view] (@local-state :selected-unit)
        [unit prop] (selected-unit-prop unit-tree (ot/uid old-unit) (old-prop :db/id))]
    (when (and prop unit)
      (let [pos (prop :orgpad/unit-position)
            width (prop :orgpad/unit-width) height (prop :orgpad/unit-height)
            bw (prop :orgpad/unit-border-width)
            style (merge {:width (+ width (* 2 bw))
                          :height (+ height (* 2 bw))}
                         (css/transform { :translate [(- (pos 0) 2) (- (pos 1) 2)] }))]
        [:div {:key "node-unit-editor" :ref "unit-editor-node"}
         [:div {:className "map-view-unit-selected simple"
                :style style
                :key 0
                :onDoubleClick (jev/make-block-propagation #(enable-quick-edit local-state))
                :onMouseDown (jev/make-block-propagation #(start-unit-move local-state %))
                :onTouchStart (jev/make-block-propagation #(start-unit-move local-state (aget % "touches" 0)))
                }]]))))

(defn- close-link-menu
  [local-state]
  (js/console.log "close link menu")
  (js/setTimeout
   #(swap! local-state merge { :link-menu-show :none }) 200))

(defn- remove-link
  [component unit local-state]
  (swap! local-state assoc :selected-link nil)
  (lc/transact! component [[ :orgpad.units/map-view-link-remove (ot/uid unit) ]]))

(defn- edge-unit-editor
  [component {:keys [view] :as unit-tree} app-state local-state]
  (let [select-link (@local-state :selected-link)]
    (when (and select-link (= (@local-state :link-menu-show) :yes))
      (let [[old-unit old-prop _ _ _ mid-pt] select-link
            [unit prop] (selected-unit-prop unit-tree (ot/uid old-unit) (old-prop :db/id))]
        (when (and prop unit)
           [:div {}
            (mc/circle-menu
             (merge edge-menu-conf { :center-x (mid-pt 0)
                                     :center-y (mid-pt 1)
                                     :onMouseDown jev/block-propagation
                                     ;; :onMouseUp jev/block-propagation
                                    })
             [:i.fa.fa-cogs.fa-lg { :title "Properties" :onMouseDown #(close-link-menu local-state) } ]
             [:i.fa.fa-pencil-square-o.fa-lg
              {:title "Edit"
               :onMouseUp #(omt/open-unit component (assoc-in unit [:view :orgpad/view-type] :orgpad/atomic-view))
               } ]
             [:i.fa.fa-remove.fa-lg { :title "Remove" :onMouseDown #(remove-link component unit local-state) } ]
           )])))))

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
    [ :div.map-view-border-edit {}
     [ :div.center (if (= action :orgpad.units/map-view-unit-border-color)
                     "Border Color"
                     "Background Color") ]
     (cpicker/color-picker color {} on-change) ] ))

(defn- render-border-width1
  [{:keys [prop] :as params}]
  [ :div.map-view-border-edit {}
   [:div.center "Border Width"]
   (render-slider (merge params {:max 20
                                 :prop-name :orgpad/unit-border-width
                                 :action :orgpad.units/map-view-unit-border-width })) ])


(defn- render-border-radius1
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

(defn- render-border-style1
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


(defn- render-props-menu1
  [params]
  [:div.map-props-toolbar {:key "prop-menu"}
   (render-color-picker1 (assoc params :action :orgpad.units/map-view-unit-border-color))
   (render-color-picker1 (assoc params :action :orgpad.units/map-view-unit-bg-color))
   (render-border-width1 params)
   (render-border-radius1 params)
   (render-border-style1 params)])

(defn- node-unit-editor-static
  [component {:keys [view] :as unit-tree} app-state local-state]
  (let [[old-unit old-prop parent-view] (@local-state :selected-unit)
        [unit prop] (selected-unit-prop unit-tree (ot/uid old-unit) (old-prop :db/id))
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
    [ :div.map-view-border-edit {}
     [ :div.center "Line Color" ]
     (cpicker/color-picker color {} (fn [c]
                                      (lc/transact! component [[ :orgpad.units/map-view-link-color
                                                                { :prop prop
                                                                  :parent-view parent-view
                                                                  :unit-tree unit
                                                                  :color c } ]]))) ] ))

(defn- render-link-width1
  [{:keys [component unit prop parent-view local-state]}]
  [ :div.map-view-border-edit {}
   [:div.center "Line Width"]
   (render-slider {:component component :unit unit :prop prop
                   :parent-view parent-view :local-state local-state
                   :max 20
                   :prop-name :orgpad/link-width
                   :action :orgpad.units/map-view-line-width }) ])

(defn- render-link-style1
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

(defn- render-edge-prop-menu
  [params]
  [:div.map-props-toolbar
   (render-link-color-picker1 params)
   (render-link-width1 params)
   (render-link-style1 params)])

(defn- edge-unit-editor-static
  [component {:keys [unit view] :as unit-tree} app-state local-state]
  (let [select-link (@local-state :selected-link)]
    (when select-link
      (let [[old-unit old-prop _ _ _ mid-pt] select-link
            [unit prop] (selected-unit-prop unit-tree (ot/uid old-unit) (old-prop :db/id))
            params {:component component :unit unit :prop prop :parent-view view :local-state local-state}]
        (render-edge-prop-menu params)))))

(rum/defcc unit-editor-static < lc/parser-type-mixin-context
  [component unit-tree app-state local-state]
  (let [select-unit (@local-state :selected-unit)]
    [:div {:onMouseDown jev/block-propagation :onTouchStart jev/block-propagation}
     (if select-unit
       (node-unit-editor-static component unit-tree app-state local-state)
       (edge-unit-editor-static component unit-tree app-state local-state))]))
