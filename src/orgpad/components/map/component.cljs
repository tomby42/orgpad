(ns ^{:doc "Map component"}
  orgpad.components.map.component
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.cycle.life :as lc]
            [orgpad.components.registry :as registry]
            [orgpad.components.menu.circle.component :as mc]
            [orgpad.components.node :as node]
            [orgpad.components.map.unit :as munit]
            [orgpad.tools.css :as css]
            [orgpad.tools.js-events :as jev]
            [orgpad.tools.orgpad :as ot]
            [orgpad.tools.rum :as trum]
            [orgpad.tools.geom :as geom]
            [orgpad.tools.jcolls :as jcolls]
            [orgpad.tools.geocache :as geocache]))

(def ^:private init-state
  { :show-local-menu false
    :local-mode :none
    :quick-edit false
    :canvas-mode :canvas-move
    :mouse-x 0
    :mouse-y 0 })

(def ^:private menu-conf {
  :always-open? true
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

(defn- hide-local-menu
  [component]
  (let [local-state (trum/comp->local-state component)]
    (swap! local-state merge { :show-local-menu false })))

(defn- show-local-menu
  [component]
  (let [local-state (trum/comp->local-state component)]
    (swap! local-state merge { :show-local-menu false })))

(defn- create-pair-unit
  [component {:keys [unit view] :as unit-tree} pos]
  (lc/transact! component
                [[ :orgpad.units/new-pair-unit
                   { :parent (unit :db/id)
                     :view-name (view :orgpad/view-name)
                     :transform (view :orgpad/transform)
                     :position pos } ]] ))

(defn- start-canvas-move
  [local-state-atom ev]
  (swap! local-state-atom
         merge { :local-mode :canvas-move
                 :show-local-menu false
                 :start-mouse-x (.-clientX ev)
                 :start-mouse-y (.-clientY ev)
                 :mouse-x (.-clientX ev)
                 :mouse-y (.-clientY ev) }))

(defn- do-create-pair-unit
  [component unit-tree pos ev]
  (create-pair-unit component unit-tree pos)
  (hide-local-menu component)
  (.stopPropagation ev))

(defn- toggle-canvas-mode
  [local-state]
  (swap! local-state update :canvas-mode #(if (= % :canvas-move) :canvas-select :canvas-move)))

(defn render-local-menu
  [component unit-tree app-state local-state-atom]
  (let [local-state @local-state-atom]
    (when (or (local-state :show-local-menu) (= (local-state :local-mode) :canvas-move))
      (let [pos { :center-x (local-state :mouse-x)
                  :center-y (local-state :mouse-y) }]
        [:div { :style { :display (if (= (local-state :local-mode) :canvas-move) "none" "block") } }
        (mc/circle-menu (merge menu-conf pos { :onMouseDown jev/block-propagation
                                               :onTouchStart jev/block-propagation
                                               :onTouchEnd jev/block-propagation
                                               :onMouseUp jev/block-propagation })
                        [ :i { :className "fa fa-file-text-o fa-lg"
                               :title "Create new unit"
                               :onMouseDown #(.stopPropagation %)
                               :onMouseUp #(do-create-pair-unit component unit-tree pos %)
                              }
]
                        [ :i { :className (if (= (:canvas-mode local-state) :canvas-select) "fa fa-crop fa-lg" "fa fa-arrows fa-lg")
                               :title (if (= (:canvas-mode local-state) :canvas-select) "Toggle to move mode" "Toggle to select mode")
                               :onClick #(toggle-canvas-mode local-state-atom)
 } ]
                        [ :i { :className "fa fa-plus fa-lg" :title "Zoom in" } ]
                        [ :i { :className "fa fa-minus fa-lg" :title "Zoom out" } ]
                        [ :i { :className "fa fa-close fa-lg"
                               :title "Hide menu"
                               :onMouseUp #(hide-local-menu component) } ] ) ]
        ) )))

(defn handle-mouse-down
  [component unit-tree app-state ev]
  (let [local-state (trum/comp->local-state component)]
    (swap! local-state merge { :mouse-x (.-clientX ev)
                               :mouse-y (.-clientY ev)
                               :start-mouse-x (.-clientX ev)
                               :start-mouse-y (.-clientY ev)
                               :selected-unit nil
                               :selected-link nil
                               :local-mode (if (= (app-state :mode) :write)
                                             :mouse-down
                                             :canvas-move)
                               :quick-edit false
                               :show-local-menu false })
    (lc/transact! component [[ :orgpad.units/deselect-all {:pid (ot/uid unit-tree)} ]])))

(defn- make-link
  [component unit-tree local-state pos]
  (lc/transact! component [[ :orgpad.units/try-make-new-link-unit
                            { :map-unit-tree unit-tree
                              :begin-unit-id (-> @local-state :selected-unit (nth 0) ot/uid)
                              :position pos }]]))

(defn- make-links
  [component unit-tree selection pos]
  (lc/transact! component  [[:orgpad.units/try-make-new-links-unit
                             {:unit-tree unit-tree
                              :selection selection
                              :position pos}]]))

(defn- stop-canvas-move
  [component { :keys [unit view] } local-state]
  (lc/transact! component
                [[ :orgpad.units/map-view-canvas-move
                   { :view view
                     :unit-id (unit :db/id)
                     :old-pos [(@local-state :start-mouse-x)
                               (@local-state :start-mouse-y)]
                     :new-pos [(@local-state :mouse-x)
                               (@local-state :mouse-y)] }]])
  (swap! local-state merge { :show-local-menu false }))


(defn- get-transformed-bb
  [local-state {:keys [orgpad/transform]}]
  (let [bb (geom/points-bbox [(:start-mouse-x local-state) (:start-mouse-y local-state)]
                             [(:mouse-x local-state) (:mouse-y local-state)])
        pos (geom/screen->canvas transform (bb 0))
        pos1 (geom/screen->canvas transform (bb 1))]
    [pos pos1]))

(defn- select-units-by-bb
  [component unit-tree local-state]
  (let [bb (get-transformed-bb @local-state (:view unit-tree))]
    (lc/transact! component
                  [[:orgpad.units/map-view-select-units-by-bb
                    {:unit-tree unit-tree
                     :bb bb}]])))

(defn- handle-mouse-up
  [component unit-tree app-state ev]
  (let [local-state (trum/comp->local-state component)]
    (case (:local-mode @local-state)
      :mouse-down (swap! local-state merge { :show-local-menu true })
      :canvas-move (stop-canvas-move component unit-tree local-state)
      :make-link (make-link component unit-tree local-state [(.-clientX ev) (.-clientY ev)])
      :link-shape (when (= (@local-state :link-menu-show) :maybe)
                    (swap! local-state assoc :link-menu-show :yes))
      :choose-selection (select-units-by-bb component unit-tree local-state)
      :make-links (make-links component unit-tree (-> @local-state :selected-units second)
                              [(.-clientX ev) (.-clientY ev)])
      nil)
    (swap! local-state merge { :local-mode :none })))

(defn- update-mouse-position
  [local-state ev]
  (swap! local-state merge { :mouse-x (.-clientX ev)
                             :mouse-y (.-clientY ev) }))

(def ^:private tr-rex (js/RegExp "translate\\(([-0-9.]+)px, ([-0-9.]+)px"))
(def ^:private sc-rex (js/RegExp "scale.*"))

(defn- canvas-move
  [component { :keys [unit view] :as unit-tree } app-state local-state ev]
  (let [pel (-> component rum/state deref (trum/ref-node "component-node"))
        el (aget pel "children" 0 "children" 0)
        tr (aget el "style" "transform")
        t (.exec tr-rex tr)
        s (.exec sc-rex tr)
        x (js/parseInt (aget t 1))
        y (js/parseInt (aget t 2))]
    (aset el "style" "transform" (str "translate("
                                      (+ x (- (.-clientX ev) (@local-state :mouse-x))) "px, "
                                      (+ y (- (.-clientY ev) (@local-state :mouse-y))) "px) "
                                      s)))
  (comment
  (lc/transact! component
                [[ :orgpad.units/map-view-canvas-move
                   { :view view
                     :unit-id (unit :db/id)
                     :old-pos [(@local-state :mouse-x)
                               (@local-state :mouse-y)]
                     :new-pos [(.-clientX ev)
                               (.-clientY ev)] }]])
  )
  (update-mouse-position local-state ev))

(defn- unit-change
  [component local-state ev action]
  (let [[unit-tree prop parent-view] (@local-state :selected-unit)]
    (lc/transact! component
                  [[ action
                    { :prop prop
                      :parent-view parent-view
                      :unit-tree unit-tree
                      :old-pos [(@local-state :mouse-x)
                                (@local-state :mouse-y)]
                      :new-pos [(.-clientX ev)
                                (.-clientY ev)] }]])
    (update-mouse-position local-state ev)))

(defn- units-change
  [component local-state ev action]
  (let [[unit-tree selection] (@local-state :selected-units)]
    (lc/transact! component
                  [[ :orgpad.units/map-view-repeat-action
                    { :unit-tree unit-tree
                      :selection selection
                      :action action
                      :old-pos [(@local-state :mouse-x)
                                (@local-state :mouse-y)]
                      :new-pos [(.-clientX ev)
                                (.-clientY ev)] }]])
    (update-mouse-position local-state ev)))

(defn- update-link-shape
  [component local-state ev]
  (let [[unit-tree prop parent-view start-pos end-pos mid-pt] (@local-state :selected-link)]
    (swap! local-state assoc :link-menu-show :none)
    (lc/transact! component
                  [[ :orgpad.units/map-view-link-shape
                    { :prop prop
                      :parent-view parent-view
                      :unit-tree unit-tree
                      :start-pos start-pos
                      :end-pos end-pos
                      :mid-pt mid-pt
                      :pos [(.-clientX ev)
                            (.-clientY ev)] }]])
    (update-mouse-position local-state ev)))

(defn- try-start-selection
  [local-state ev]
  (if (= (:canvas-mode @local-state) :canvas-select)
    (swap! local-state merge {:local-mode :choose-selection
                              :show-local-menu false
                              :start-mouse-x (.-clientX ev)
                              :start-mouse-y (.-clientY ev)
                              :mouse-x (.-clientX ev)
                              :mouse-y (.-clientY ev) })
    (start-canvas-move local-state ev)))

(defn- handle-mouse-move
  [component unit-tree app-state ev]
  (let [local-state (trum/comp->local-state component)]
    (case (@local-state :local-mode)
      :canvas-move (canvas-move component unit-tree app-state local-state (jev/stop-propagation ev))
      :unit-move (unit-change component local-state (jev/stop-propagation ev) :orgpad.units/map-view-unit-move)
      :unit-resize (unit-change component local-state (jev/stop-propagation ev) :orgpad.units/map-view-unit-resize)
      :make-link (update-mouse-position local-state (jev/stop-propagation ev))
      :link-shape (update-link-shape component local-state (jev/stop-propagation ev))
      :try-unit-move (do
                       (swap! local-state assoc :local-mode :unit-move :pre-quick-edit 0)
                       (unit-change component local-state (jev/stop-propagation ev) :orgpad.units/map-view-unit-move))
      :units-move (units-change component local-state (jev/stop-propagation ev) :orgpad.units/map-view-unit-move)
      :mouse-down (try-start-selection local-state (jev/stop-propagation ev))
      :choose-selection (update-mouse-position local-state (jev/stop-propagation ev))
      :make-links (update-mouse-position local-state (jev/stop-propagation ev))
      nil)
    (when (not= (@local-state :local-mode) :default-mode)
      (.preventDefault ev))))

(defn- handle-blur
  [component unit-tree app-state ev]
  (let [local-state (trum/comp->local-state component)]
    (swap! local-state merge init-state)))

(defn- render-selection-box
  [local-state view]
  (let [[pos pos1] (geom/points-bbox [(:start-mouse-x local-state) (:start-mouse-y local-state)]
                                     [(:mouse-x local-state) (:mouse-y local-state)])
        [width height] (geom/-- pos1 pos)]
    [:div.selection-box {:style (merge {:width width
                                        :height height}
                                       (css/transform {:translate pos}))}]))

(defn normalize-mouse-data
  [ev]
  (let [evt (.-nativeEvent ev)]
    (if (zero? (.-detail evt))
      (.-wheelDelta evt)
      (* -120 (.-detail evt)))))

(defn- handle-wheel
  [component {:keys [unit view] :as unit-tree} app-state ev]
  (let [zoom (if (< (normalize-mouse-data ev) 0) 0.95 1.05)]
    (lc/transact! component [[:orgpad.units/map-view-canvas-zoom
                              {:view view
                               :parent-id (:db/id unit)
                               :pos [(.-clientX ev) (.-clientY ev)]
                               :zoom zoom}]])))

(defn- render-write-mode
  [component unit-tree app-state]
  (let [local-state (trum/comp->local-state component)]
    (html
     [ :div { :className "map-view"
              :ref "component-node"
              :onMouseDown #(do
                              (handle-mouse-down component unit-tree app-state %)
                              (.stopPropagation %))
              ;;:onTouchStart #(do
              ;;                 (handle-mouse-down component unit-tree app-state (aget % "touches" 0))
              ;;                 (.stopPropagation %))
              :onMouseUp #(handle-mouse-up component unit-tree app-state %)
              :onMouseMove #(handle-mouse-move component unit-tree app-state %)
              :onBlur #(handle-blur component unit-tree app-state %)
              :onMouseLeave #(handle-blur component unit-tree app-state %)
              :onWheel #(handle-wheel component unit-tree app-state %) }
       (munit/render-mapped-children-units component unit-tree app-state local-state)
       (render-local-menu component unit-tree app-state local-state)
       (when (= (:local-mode @local-state) :choose-selection)
         (render-selection-box @local-state (:view unit-tree)))
      (when (> (count (get-in app-state [:selections (ot/uid unit-tree)])) 1)
        (munit/render-selected-children-units component unit-tree app-state local-state))
      ])))

(defn- render-read-mode
  [component unit-tree app-state]
  (let [local-state (trum/comp->local-state component)]
    (html
     [ :div { :className "map-view"
              :ref "component-node"
              :onMouseDown #(do
                              (handle-mouse-down component unit-tree app-state %)
                              (.stopPropagation %))
              :onTouchStart #(do
                               (handle-mouse-down component unit-tree app-state (aget % "touches" 0))
                               (.stopPropagation %))
              :onMouseUp #(handle-mouse-up component unit-tree app-state %)
              :onMouseMove #(handle-mouse-move component unit-tree app-state %)
              :onBlur #(handle-blur component unit-tree app-state %)
              :onMouseLeave #(handle-blur component unit-tree app-state %) }
      (munit/render-mapped-children-units component unit-tree app-state local-state)
      (when (> (count (get-in app-state [:selections (ot/uid unit-tree)])) 1)
        (munit/render-selected-children-units component unit-tree app-state local-state))
      ])))

(defn- prevent-default
  [ev]
  (fn []
    (try
      (when (.-preventDefault ev)
        (.preventDefault ev))
      (catch :default e e))))

(def ^:private handle-touch-event
  { :did-mount
    (fn [state]
      (let [move-cb
            (fn [ev]
              (let [component (state :rum/react-component)
                    state' @(rum/state component)
                    [unit-tree app-state] (state' :rum/args)]
                (handle-mouse-move component unit-tree app-state
                                   #js { :preventDefault (prevent-default ev)
                                         :stopPropagation (fn [] (.stopPropagation ev))
                                         :clientX (aget ev "touches" 0 "clientX")
                                         :clientY (aget ev "touches" 0 "clientY") })))
            end-cb
            (fn [ev]
              (let [component (state :rum/react-component)
                    state' @(rum/state component)
                    [unit-tree app-state] (state' :rum/args)]
                (handle-mouse-up component unit-tree app-state
                                 #js { :preventDefault (prevent-default ev)
                                       :stopPropagation (fn [] (.stopPropagation ev))
                                       :clientX (-> state :rum/local deref :mouse-x)
                                       :clientY (-> state :rum/local deref :mouse-y) })))]
        (swap! (state :rum/local) merge { :touch-move-event-handler move-cb
                                          :touch-end-event-handler end-cb })
        (js/document.addEventListener "touchmove" move-cb)
        (js/document.addEventListener "touchend" end-cb))

      state)

    :will-unmount
    (fn [state]
      (js/document.removeEventListener "touchmove" (-> state :rum/local deref :touch-move-event-handler))
      (js/document.removeEventListener "touchend" (-> state :rum/local deref :touch-end-event-handler))
      (swap! (state :rum/local) dissoc :touch-move-event-handler)
      (swap! (state :rum/local) dissoc :touch-end-event-handler)
      state)
   })

(def ^:private component-size-mixin
  (trum/gen-update-mixin
   (fn [state]
     (let [node (trum/ref-node state "component-node")
           id (-> state trum/args first ot/uid)
           bbox (.getBoundingClientRect node)]
       ;; (js/console.log bbox)
       (lc/set-global-cache (trum/component state)
                            id
                            "bbox"
                            bbox)))))
(defn- get-default-bbox
  []
  #js { :left 0 :right js/window.innerWidth :top 0 :bottom js/window.innerHeight })

(defn- pick-visible-children
  [unit view-unit old-node global-cache]
  (let [id (unit :db/id)]
    (if (aget global-cache id)
      (let [children-cache (if old-node
                             (persistent!
                              (reduce (fn [m n]
                                        (assoc! m (-> n (aget "value") ot/uid) n))
                                      (transient {}) (aget old-node "children")))
                             {})
            iz (/ 1 (-> view-unit :orgpad/transform :scale))
            pos (geom/*c (-> view-unit :orgpad/transform :translate)
                         iz)
            bbox (or (aget global-cache id "bbox")
                     (get-default-bbox))
            size (geom/*c [(- (.-right bbox) (.-left bbox))
                           (- (.-bottom bbox) (.-top bbox))]
                          iz)
            vis-units (geocache/visible-units global-cache id (:orgpad/view-name view-unit) pos size)]
        ;; (js/console.log "vis units" unit view-unit vis-units global-cache)
        (map (juxt identity children-cache) vis-units))
      [])))

(rum/defcc map-component < trum/istatic lc/parser-type-mixin-context (rum/local init-state) handle-touch-event component-size-mixin
  [component unit-tree app-state]
  (if (= (:mode app-state) :write)
    (render-write-mode component unit-tree app-state)
    (render-read-mode component unit-tree app-state)))

(registry/register-component-info
 :orgpad/map-view
 { :orgpad/default-view-info   { :orgpad/view-type :orgpad/map-view
                                 :orgpad/view-name "default"
                                 :orgpad/transform { :translate [0 0]
                                                     :scale     1.0 } }
   :orgpad/child-default-view-info     { :orgpad/view-type :orgpad/map-tuple-view
                                         :orgpad/view-name "default" }
   :orgpad/class               map-component
   :orgpad/child-props-types   [:orgpad.map-view/vertex-props :orgpad.map-view/link-props]
   :orgpad/child-props-default { :orgpad.map-view/vertex-props
                                 { :orgpad/view-type :orgpad.map-view/vertex-props
                                   :orgpad/view-name "default"
                                   :orgpad/unit-width 250
                                   :orgpad/unit-height 60
                                   :orgpad/unit-border-color "#009cff"
                                   :orgpad/unit-bg-color "#ffffff"
                                   :orgpad/unit-border-width 2
                                   :orgpad/unit-corner-x 5
                                   :orgpad/unit-corner-y 5
                                   :orgpad/unit-border-style "solid" }

                                :orgpad.map-view/link-props
                                 { :orgpad/view-type :orgpad.map-view/link-props
                                   :orgpad/view-name "default"
                                   :orgpad/link-color "#000000"
                                   :orgpad/link-width 2
                                   :orgpad/link-dash #js [0 0]
                                   :orgpad/link-mid-pt [0 0] } }
   :orgpad/needs-children-info true
   :orgpad/view-name           "Map View"
   :orgpad/visible-children-picker pick-visible-children
  })
