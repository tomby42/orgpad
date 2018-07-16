(ns ^{:doc "Map component"}
  orgpad.components.map.component
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.cycle.life :as lc]
            [orgpad.components.registry :as registry]
            [orgpad.components.node :as node]
            [orgpad.components.map.unit :as munit]
            [orgpad.tools.css :as css]
            [orgpad.tools.js-events :refer [mouse-node-x mouse-node-y mouse-node-rel-x mouse-node-rel-y] :as jev]
            [orgpad.tools.orgpad :as ot]
            [orgpad.tools.orgpad-manipulation :as omt]
            [orgpad.tools.rum :as trum]
            [orgpad.tools.geom :as geom :refer [-- ++ *c screen->canvas canvas->screen]]
            [orgpad.tools.jcolls :as jcolls]
            [orgpad.tools.geocache :as geocache]
            [orgpad.tools.colls :as colls]
            [orgpad.tools.dom :as dom]
            [orgpad.tools.time :as t]
            [orgpad.cycle.utils :as cu]
            [orgpad.components.map.utils :refer [mouse-pos set-mouse-pos! start-change-link-shape start-link]]))

(def ^:private init-state
  { :local-mode :none
    :quick-edit false
    :canvas-mode :canvas-move
    :mouse-x 0
    :mouse-y 0
    :link-start-x 0
    :link-start-y 0})

(defn- create-pair-unit
  [component {:keys [unit view] :as unit-tree} pos]
  (lc/transact! component
                [[ :orgpad.units/new-pair-unit
                   { :parent (unit :db/id)
                     :view-name (view :orgpad/view-name)
                     :transform (view :orgpad/transform)
                     :position pos
                     :style (lc/query component :orgpad/style {:view-type :orgpad.map-view/vertex-props-style
                                                               :style-name "default"} true) } ]] ))

(defn- start-canvas-move
  [local-state-atom ev]
  (swap! local-state-atom
         merge { :local-mode :canvas-move
                 :start-mouse-x (.-clientX ev)
                 :start-mouse-y (.-clientY ev)
                 :mouse-x (.-clientX ev)
                 :mouse-y (.-clientY ev) }))

(defn- do-create-pair-unit
  [component unit-tree pos ev]
  (create-pair-unit component unit-tree pos)
  (.stopPropagation ev))

(defn- get-rel-mouse-pos
  [component unit-tree ev]
  (let [bbox (lc/get-global-cache component (ot/uid unit-tree) "bbox")]
    [(mouse-node-rel-x bbox ev)
     (mouse-node-rel-y bbox ev)]))

(defn handle-mouse-down
  [component unit-tree app-state ev]
  (let [local-state (trum/comp->local-state component)
        pos (get-rel-mouse-pos component unit-tree ev)
        linfo (when (= (:mode app-state) :write)
                (ot/get-nearest-link unit-tree
                                     (screen->canvas (-> unit-tree :view :orgpad/transform)
                                                     pos)))]
    ;; (js/console.log "nearest link" linfo)
    ;; (jev/block-propagation ev)
    (swap! local-state merge { :mouse-x (.-clientX ev)
                               :mouse-y (.-clientY ev)
                               :start-mouse-x (.-clientX ev)
                               :start-mouse-y (.-clientY ev)
                               :selected-unit nil
                               :selected-link nil
                               :local-mode (if (= (app-state :mode) :write)
                                             (if (= (:canvas-mode @local-state) :canvas-paste)
                                               :canvas-paste
                                               :mouse-down)
                                             :canvas-move)
                               :quick-edit false })
    (set-mouse-pos! ev)
    (if (and linfo (< (-> linfo (get 3) (aget "d")) 20))
      (let [[link-unit link-prop link-info link-dist-info] linfo]
        (start-change-link-shape link-unit link-prop component (:start-pos link-info) (:end-pos link-info)
                                 [(aget link-dist-info "x") (aget link-dist-info "y")] (aget link-dist-info "t")
                                 (:cyclic? link-info) (:start-size link-info)
                                 local-state ev))
      (lc/transact! component [[ :orgpad.units/deselect-all {:pid (ot/uid unit-tree)} ]]))))

(defn- make-link
  [component unit-tree local-state pos]
  (lc/transact! component [[ :orgpad.units/try-make-new-link-unit
                            { :map-unit-tree unit-tree
                              :begin-unit-id (-> @local-state :selected-unit (nth 0) ot/uid)
                              :position pos
                              :style (lc/query component :orgpad/style
                                               {:view-type :orgpad.map-view/link-props-style
                                                :style-name "default"} true)}]]))

(defn- make-links
  [component unit-tree selection pos]
  (lc/transact! component  [[:orgpad.units/try-make-new-links-unit
                             {:unit-tree unit-tree
                              :selection selection
                              :position pos
                              :style (lc/query component :orgpad/style
                                               {:view-type :orgpad.map-view/link-props-style
                                                :style-name "default"} true)}]]))

(defn- stop-canvas-move
  [component { :keys [unit view] } local-state new-pos]
  (lc/transact! component
                [[ :orgpad.units/map-view-canvas-move
                   { :view view
                     :unit-id (unit :db/id)
                     :old-pos [(@local-state :start-mouse-x)
                               (@local-state :start-mouse-y)]
                     :new-pos new-pos }]]))

(defn- stop-unit-move
  [component local-state new-pos]
  (let [[unit-tree prop parent-view] (@local-state :selected-unit)]
    (lc/transact! component
                  [[:orgpad.units/map-view-unit-move
                    {:prop prop
                     :parent-view parent-view
                     :unit-tree unit-tree
                     :old-pos [(@local-state :start-mouse-x)
                               (@local-state :start-mouse-y)]
                     :new-pos new-pos }]])))

(defn- stop-units-move
  [component local-state new-pos]
  (let [[unit-tree selection] (@local-state :selected-units)]
    (lc/transact! component
                  [[ :orgpad.units/map-view-repeat-action
                    { :unit-tree unit-tree
                      :selection selection
                      :action :orgpad.units/map-view-unit-move
                      :old-pos [(@local-state :start-mouse-x)
                                (@local-state :start-mouse-y)]
                      :new-pos new-pos }]])))

(defn- stop-unit-resize
  [component local-state new-pos]
  (let [[unit-tree prop parent-view] (@local-state :selected-unit)]
    (lc/transact! component
                  [[:orgpad.units/map-view-unit-resize
                    {:prop prop
                     :parent-view parent-view
                     :unit-tree unit-tree
                     :old-pos [(@local-state :start-mouse-x)
                               (@local-state :start-mouse-y)]
                     :new-pos new-pos }]])))

(defn- get-transformed-bb
  [local-state {:keys [orgpad/transform]}]
  (let [bb (geom/points-bbox [(:start-mouse-x local-state) (:start-mouse-y local-state)]
                             [(:mouse-x local-state) (:mouse-y local-state)])
        pos (screen->canvas transform (bb 0))
        pos1 (screen->canvas transform (bb 1))]
    [pos pos1]))

(defn- select-units-by-bb
  [component unit-tree local-state]
  (let [bb (get-transformed-bb @local-state (:view unit-tree))]
    (lc/transact! component
                  [[:orgpad.units/map-view-select-units-by-bb
                    {:unit-tree unit-tree
                     :bb bb}]])))

(def ^:private last-unit-created-ts (volatile! 0))

(defn- resolve-mouse-down
  [component unit-tree local-state ev]
  (let [pos (get-rel-mouse-pos component unit-tree ev)]
    (when (and (= (:canvas-mode @local-state) :canvas-create-unit)
               (not (.-isTouch ev))
               (< 250 (- (t/now) @last-unit-created-ts)))
      (create-pair-unit component unit-tree {:center-x (get pos 0)
                                             :center-y (get pos 1)})))

  (when (not (.-isTouch ev))
    (vreset! last-unit-created-ts (t/now))))

(defn- handle-mouse-up
  [component unit-tree app-state ev]
  (let [local-state (trum/comp->local-state component)
        bbox (lc/get-global-cache component (ot/uid unit-tree) "bbox")]
    ;; (js/console.log "handle-mouse-up" (:local-mode @local-state) (:canvas-mode @local-state))
    (case (:local-mode @local-state)
      :mouse-down (resolve-mouse-down component unit-tree local-state ev)
      :canvas-move (stop-canvas-move component unit-tree local-state [(.-clientX ev) (.-clientY ev)])
      :unit-move (stop-unit-move component local-state [(.-clientX ev) (.-clientY ev)])
      :unit-resize (stop-unit-resize component local-state [(.-clientX ev) (.-clientY ev)])
      :units-move (stop-units-move component local-state [(.-clientX ev) (.-clientY ev)])
      :make-link (make-link component unit-tree local-state [(mouse-node-rel-x bbox ev) (mouse-node-rel-y bbox ev)])
      :link-shape (when (= (@local-state :link-menu-show) :maybe)
                    (swap! local-state assoc :link-menu-show :yes))
      :choose-selection (select-units-by-bb component unit-tree local-state)
      :make-links (make-links component unit-tree (-> @local-state :selected-units second)
                              [(mouse-node-rel-x bbox ev) (mouse-node-rel-y bbox ev)])
      :canvas-paste (omt/paste-units-from-clipboard component unit-tree app-state (get-rel-mouse-pos component unit-tree ev))
      nil)
    (swap! local-state merge { :local-mode :none })
    (js/setTimeout
     #(swap! local-state merge { :local-mode :none }) 0)))

(defn- update-mouse-position
  [local-state ev]
  (swap! local-state merge { :mouse-x (.-clientX ev)
                             :mouse-y (.-clientY ev) }))

(defn- canvas-move
  [component { :keys [unit view] :as unit-tree } app-state local-state ev]
  (let [pel (-> component rum/state deref (trum/ref-node "component-node"))
        el (aget pel "children" 0 "children" 0)]
    (dom/update-translate el (.-clientX ev) (.-clientY ev)
                          (@mouse-pos :mouse-x) (@mouse-pos :mouse-y) 1)))

(defn- units-move
  [parent-view local-state ev]
  (let [el (jcolls/aget-safe (:unit-editor-node @local-state) "children" 0)]
    (when el
      (dom/update-translate el (.-clientX ev) (.-clientY ev)
                            (@mouse-pos :mouse-x) (@mouse-pos :mouse-y)
                            (-> parent-view :orgpad/transform :scale)))))

;; (defn- unit-move
;;   [parent-view local-state ev]
;;   (let [el (jcolls/aget-safe (:unit-editor-node @local-state) "children" 0)
;;         pos (get-in @local-state [:selected-unit 1 :orgpad/unit-position])]
;;     (when el
;;       (dom/set-translate el (pos 0) (pos 1) (.-clientX ev) (.-clientY ev)
;;                          (@local-state :start-mouse-x) (@local-state :start-mouse-y)
;;                          (-> parent-view :orgpad/transform :scale)))))

(defn- unit-move
  [parent-view local-state ev]
  (let [el (jcolls/aget-safe (:unit-editor-node @local-state) "children" 0)
        width (get-in @local-state [:selected-unit 1 :orgpad/unit-width])
        height (get-in @local-state [:selected-unit 1 :orgpad/unit-height])
        pos (-- (get-in @local-state [:selected-unit 1 :orgpad/unit-position])
                [(/ width 2) (/ height 2)])]
    (when el
      (dom/set-translate el (pos 0) (pos 1) (.-clientX ev) (.-clientY ev)
                         (@local-state :start-mouse-x) (@local-state :start-mouse-y)
                         (-> parent-view :orgpad/transform :scale)))))


;; (defn- unit-resize
;;   [parent-view local-state ev]
;;   (let [el (jcolls/aget-safe (:unit-editor-node @local-state) "children" 0)]
;;     (when el
;;       (dom/update-size el (.-clientX ev) (.-clientY ev)
;;                        (@mouse-pos :mouse-x) (@mouse-pos :mouse-y)
;;                        (-> parent-view :orgpad/transform :scale)))))

(defn- unit-resize
  [parent-view local-state ev]
  (let [el (jcolls/aget-safe (:unit-editor-node @local-state) "children" 0)]
    (when el
      (dom/update-size-translate el (.-clientX ev) (.-clientY ev)
                                 (@mouse-pos :mouse-x) (@mouse-pos :mouse-y)
                                 (-> parent-view :orgpad/transform :scale)))))


(defn- update-link-shape
  [component local-state ev]
  (let [[unit-tree prop parent-view start-pos end-pos mid-pt t cyclic? start-size] (@local-state :selected-link)
        bbox (lc/get-global-cache component (-> parent-view :orgpad/refs first :db/id) "bbox")]
    (swap! local-state assoc :link-menu-show :none)
    (lc/transact! component
                  [[:orgpad.units/map-view-link-shape
                    {:prop prop
                     :parent-view parent-view
                     :unit-tree unit-tree
                     :start-pos start-pos
                     :end-pos end-pos
                     :mid-pt mid-pt
                     :t t
                     :cyclic? cyclic?
                     :start-size start-size
                     :pos [(mouse-node-rel-x bbox ev)
                           (mouse-node-rel-y bbox ev)]}]])
    (update-mouse-position local-state ev)))

(defn- try-start-selection
  [local-state ev]
  (if (= (:canvas-mode @local-state) :canvas-select)
    (swap! local-state merge {:local-mode :choose-selection
                              :start-mouse-x (.-clientX ev)
                              :start-mouse-y (.-clientY ev)
                              :mouse-x (.-clientX ev)
                              :mouse-y (.-clientY ev) })
    (start-canvas-move local-state ev)))

(defn- resolve-unit-move
  [unit-tree app-state local-state ev]
  ;; (set-mouse-pos! ev)
  (if (and (= (:mode app-state) :write)
           (= (:canvas-mode @local-state) :canvas-create-unit))
    (start-link local-state ev)
    (do
      (unit-move (:view unit-tree) local-state ev)
      (swap! local-state assoc :local-mode :unit-move :pre-quick-edit 0))))

(defn- handle-mouse-move
  [component unit-tree app-state ev]
  (let [local-state (trum/comp->local-state component)]
    (case (@local-state :local-mode)
      :canvas-move (canvas-move component unit-tree app-state local-state (jev/stop-propagation ev))
      :unit-move (unit-move (:view unit-tree) local-state (jev/stop-propagation ev))
      :unit-resize (unit-resize (:view unit-tree) local-state (jev/stop-propagation ev))
      :make-link (update-mouse-position local-state (jev/stop-propagation ev))
      :link-shape (update-link-shape component local-state (jev/stop-propagation ev))
      :try-unit-move (resolve-unit-move unit-tree app-state local-state (jev/stop-propagation ev))
      :units-move (units-move (:view unit-tree) local-state (jev/stop-propagation ev))
      :mouse-down (try-start-selection local-state (jev/stop-propagation ev))
      :choose-selection (update-mouse-position local-state (jev/stop-propagation ev))
      :make-links (update-mouse-position local-state (jev/stop-propagation ev))
      nil)

    (set-mouse-pos! ev)

    (when (not= (@local-state :local-mode) :default-mode)
      (jev/block-propagation ev))
    ))

(defn- handle-blur
  [component unit-tree app-state ev]
  (let [local-state (trum/comp->local-state component)]
    (swap! local-state merge init-state)))

(defn- render-selection-box
  [component unit-tree local-state view]
  (let [[pos pos1] (geom/points-bbox [(:start-mouse-x local-state) (:start-mouse-y local-state)]
                                     [(:mouse-x local-state) (:mouse-y local-state)])
        [width height] (-- pos1 pos)
        bbox (lc/get-global-cache component (ot/uid unit-tree) "bbox")]
    [:div.selection-box {:style (merge {:width width
                                        :height height}
                                       (css/transform {:translate (-- pos [(.-left bbox) (.-top bbox)])}))}]))

(defn normalize-mouse-data
  [ev]
  (let [evt (.-nativeEvent ev)]
    (if (zero? (.-detail evt))
      (if (.-wheelDelta evt)
        (.-wheelDelta evt)
        (js* "evt.deltaX  || evt.deltaY || evt.deltaZ"))
      (* -120 (.-detail evt)))))

(defn- handle-wheel
  [component {:keys [unit view] :as unit-tree} app-state ev]
  (let [zoom (if (< (normalize-mouse-data ev) 0) 0.95 1.05)]
    (lc/transact! component [[:orgpad.units/map-view-canvas-zoom
                              {:view view
                               :parent-id (:db/id unit)
                               :pos [(.-clientX ev) (.-clientY ev)]
                               :zoom zoom}]])))

(defn- handle-double-click
  [component unit-tree ev]
  (do-create-pair-unit component unit-tree {:center-x (mouse-node-x ev)
                                            :center-y (mouse-node-y ev)} ev))

(defn- handle-key-down
  [component unit-tree app-state local-state ev]
  (when (= (:mode app-state) :write)
    ;; (js/console.log "down" (.-code ev))
    (if (or (.-ctrlKey ev) (.-metaKey ev))
      (let [data (get-in app-state [:clipboards (ot/uid unit-tree)])]
        (case (.-code ev)
          "KeyC" (omt/copy-units-to-clipboard component unit-tree app-state)
          "KeyV" (omt/paste-units-from-clipboard component unit-tree app-state (get-rel-mouse-pos component unit-tree #js {:clientX (:mouse-x @mouse-pos)
                                                                                                                           :clientY (:mouse-y @mouse-pos)}))
          "ControlLeft" (swap! local-state assoc :canvas-mode :canvas-create-unit)
          nil))
      (case (.-code ev)
        "ShiftLeft" (swap! local-state assoc :canvas-mode :canvas-select)
        nil))))

(defn- handle-key-up
  [component unit-tree app-state local-state ev]
  (when (= (:mode app-state) :write)
    ;; (js/console.log "up" (.-code ev))
    (case (.-code ev)
      "ControlLeft" (swap! local-state assoc :canvas-mode :canvas-move)
      "ShiftLeft" (swap! local-state assoc :canvas-mode :canvas-move)
      nil)))

(defn- render-write-mode
  [component unit-tree app-state]
  (let [local-state (trum/comp->local-state component)]
    (html
     [ :div { :className "map-view"
              :ref "component-node"
              :tabIndex "1"
              :onMouseDown #(do
                              (handle-mouse-down component unit-tree app-state %)
                              (jev/block-propagation %))
              :onTouchStart #(do
                               (handle-mouse-down component unit-tree app-state (aget % "touches" 0))
                               (jev/block-propagation %))
              :onMouseUp #(do
                            (handle-mouse-up component unit-tree app-state %)
                            (jev/block-propagation %))
              :onMouseMove #(handle-mouse-move component unit-tree app-state %)
              :onBlur #(handle-blur component unit-tree app-state %)
              :onMouseLeave #(handle-blur component unit-tree app-state %)
              :onDoubleClick #(handle-double-click component unit-tree %)
              :onWheel (jev/make-block-propagation #(handle-wheel component unit-tree app-state %)) }
       (munit/render-mapped-children-units component unit-tree app-state local-state)
       (when (= (:local-mode @local-state) :choose-selection)
         (render-selection-box component unit-tree @local-state (:view unit-tree)))
      (when (and (:enable-experimental-features? app-state)
                 (> (count (get-in app-state [:selections (ot/uid unit-tree)])) 1))
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
                              (jev/block-propagation %))
              :onTouchStart #(do
                               (handle-mouse-down component unit-tree app-state (aget % "touches" 0))
                               (jev/block-propagation %))
              :onMouseUp #(do
                           (handle-mouse-up component unit-tree app-state %)
                           (jev/block-propagation %))
              :onMouseMove #(handle-mouse-move component unit-tree app-state %)
              :onBlur #(handle-blur component unit-tree app-state %)
              :onMouseLeave #(handle-blur component unit-tree app-state %)
              :onWheel (jev/make-block-propagation #(handle-wheel component unit-tree app-state %)) }
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
                                       :isTouch true
                                       :clientX (:mouse-x @mouse-pos)
                                       :clientY (:mouse-y @mouse-pos) })))
            key-down-cb
            (fn [ev]
              (let [component (state :rum/react-component)
                    state' @(rum/state component)
                    [unit-tree app-state] (state' :rum/args)]
                (handle-key-down component unit-tree app-state (-> state :rum/local ) ev)))
            key-up-cb
            (fn [ev]
              (let [component (state :rum/react-component)
                    state' @(rum/state component)
                    [unit-tree app-state] (state' :rum/args)]
                (handle-key-up component unit-tree app-state (-> state :rum/local ) ev)))]
        (swap! (state :rum/local) merge { :touch-move-event-handler move-cb
                                          :touch-end-event-handler end-cb
                                          :key-down-event-handler key-down-cb
                                          :key-up-event-hadler key-up-cb})
        (js/document.addEventListener "touchmove" move-cb)
        (js/document.addEventListener "touchend" end-cb)
        (js/document.addEventListener "keydown" key-down-cb)
        (js/document.addEventListener "keyup" key-up-cb))
      state)

    :will-unmount
    (fn [state]
      (js/document.removeEventListener "touchmove" (-> state :rum/local deref :touch-move-event-handler))
      (js/document.removeEventListener "touchend" (-> state :rum/local deref :touch-end-event-handler))
      (js/document.removeEventListener "keydown" (-> state :rum/local deref :key-down-event-handler))
      (js/document.removeEventListener "keyup" (-> state :rum/local deref :key-up-event-handler))
      (swap! (state :rum/local) dissoc :touch-move-event-handler)
      (swap! (state :rum/local) dissoc :touch-end-event-handler)
      (swap! (state :rum/local) dissoc :key-down-event-handler)
      (swap! (state :rum/local) dissoc :key-up-event-handler)
      state)
   })

(def ^:private component-size-mixin
  (trum/gen-update-mixin
   (fn [state]
     (let [node (trum/ref-node state "component-node")
           id (-> state trum/args first ot/uid)
           bbox (.getBoundingClientRect node)
           component (trum/component state)]
       ;; (js/console.log bbox)
       (lc/set-global-cache component id "component" component)
       (lc/set-global-cache component id "bbox" bbox)))))

(defn- get-default-bbox
  []
  #js { :left 0 :right js/window.innerWidth :top 0 :bottom js/window.innerHeight })

(defn- pick-visible-children
  [unit view-unit old-node global-cache & [keep-units]]
  (let [id (unit :db/id)]
    (if (aget global-cache id)
      (let [children-cache (cu/build-children-old-node-cache old-node)
            iz (/ 1 (-> view-unit :orgpad/transform :scale))
            pos (*c (-> view-unit :orgpad/transform :translate)
                         iz)
            bbox (or (aget global-cache id "bbox")
                     (get-default-bbox))
            size (*c [(- (.-right bbox) (.-left bbox))
                      (- (.-bottom bbox) (.-top bbox))]
                     iz)
            vis-units (geocache/visible-units global-cache id (:orgpad/view-name view-unit) pos size)]
        ;; (js/console.log "vis units" unit view-unit vis-units global-cache children-cache)
        (map (juxt identity children-cache) (concat keep-units vis-units)))
      [])))

(def ^:private istatic-local-mode
  {:should-update
   (fn [old-state new-state]
     (let [old-local (:rum/local old-state)
           new-local (:rum/local new-state)]
     (not
      (and
       (colls/shallow-eq
        (:rum/args old-state) (:rum/args new-state))
       (= (:local-mode @old-local) (:local-mode @new-local))))))})

(rum/defcc map-component < istatic-local-mode lc/parser-type-mixin-context (rum/local init-state) handle-touch-event component-size-mixin
  [component unit-tree app-state]
  (if (= (:mode app-state) :write)
    (render-write-mode component unit-tree app-state)
    (render-read-mode component unit-tree app-state)))

(defn- active-toolbar-btn?
  [local-state required]
  (when local-state (= (:canvas-mode @local-state) required)))

(registry/register-component-info
 :orgpad/map-view
 {:orgpad/default-view-info   { :orgpad/view-type :orgpad/map-view
                                :orgpad/view-name "default"
                                :orgpad/transform { :translate [0 0]
                                                    :scale     1.0 } }
  :orgpad/child-default-view-info     { :orgpad/view-type :orgpad/map-tuple-view
                                        :orgpad/view-name "default" }
  :orgpad/class               map-component
  :orgpad/child-props-types   [:orgpad.map-view/vertex-props :orgpad.map-view/link-props
                                :orgpad.map-view/vertex-props-style :orgpad.map-view/link-props-style]
  :orgpad/child-props-style-types [{:key :orgpad.map-view/vertex-props-style
                                    :name "Unit style"}
                                   {:key :orgpad.map-view/link-props-style
                                    :name "Link style"}]
  :orgpad/child-props-default { :orgpad.map-view/vertex-props
                                 { :orgpad/view-type :orgpad.map-view/vertex-props
                                   :orgpad/view-name "default"
                                   :orgpad/view-style "default"}

                                :orgpad.map-view/vertex-props-style
                                 { :orgpad/view-type :orgpad.map-view/vertex-props-style
                                   :orgpad/type :orgpad/unit-view-child
                                   :orgpad/independent true
                                   :orgpad/view-name "*"
                                   :orgpad/style-name "default"
                                   :orgpad/unit-width 320
                                   :orgpad/unit-height 150
                                   :orgpad/unit-border-color "#009cffff"
                                   :orgpad/unit-bg-color "#ffffffff"
                                   :orgpad/unit-border-width 2
                                   :orgpad/unit-corner-x 5
                                   :orgpad/unit-corner-y 5
                                   :orgpad/unit-border-style "solid"
								   :orgpad/unit-padding 5 }

                                :orgpad.map-view/link-props
                                 { :orgpad/view-type :orgpad.map-view/link-props
                                   :orgpad/view-name "default"
                                   :orgpad/view-style "default" }

                                :orgpad.map-view/link-props-style
                                 { :orgpad/view-type :orgpad.map-view/link-props-style
                                   :orgpad/type :orgpad/unit-view-child
                                   :orgpad/view-name "*"
                                   :orgpad/independent true
                                   :orgpad/style-name "default"
                                   :orgpad/link-color "#000000ff"
                                   :orgpad/link-width 2
                                   :orgpad/link-dash #js [0 0]

                                   :orgpad/link-mid-pt [0 0]
                                   :orgpad/link-type :directed
                                   :orgpad/link-arrow-pos 50 }}
  :orgpad/needs-children-info true
  :orgpad/view-name           "Map View"
  :orgpad/view-icon           "far fa-share-alt"
  :orgpad/visible-children-picker pick-visible-children

  :orgpad/toolbar [
    [{:elem :btn
      :id "zoom-in"
      :icon "far fa-search-plus"
      :title "Zoom in"
      :on-click #(lc/transact! (:component %1) [[:orgpad.units/map-view-canvas-zoom
                                                 {:view (:view %1)
                                                  :parent-id (:db/id (:unit %1))
                                                  :pos [(/ js/window.innerWidth 2)
                                                        (/ js/window.innerHeight 2)]
                                                  :zoom 1.1025}]])}
     {:elem :btn
      :id "zoom-out"
      :icon "far fa-search-minus"
      :title "Zoom out"
      :on-click #(lc/transact! (:component %1) [[:orgpad.units/map-view-canvas-zoom
                                                 {:view (:view %1)
                                                  :parent-id (:db/id (:unit %1))
                                                  :pos [(/ js/window.innerWidth 2)
                                                        (/ js/window.innerHeight 2)]
                                                  :zoom 0.9025}]])}]
    [{:elem :btn
      :id "unit-creation-mode"
      :icon "far fa-plus-square"
      :title "Creation mode"
      :on-click #(swap! (:node-state %1) assoc :canvas-mode :canvas-create-unit)
      :active #(active-toolbar-btn? (:node-state %1) :canvas-create-unit)
      :hidden #(= (:mode %1) :read)}
     {:elem :btn
      :id "moving-mode"
      :icon "far fa-arrows"
      :title "Moving mode"
      :on-click #(swap! (:node-state %1) assoc :canvas-mode :canvas-move)
      :active #(active-toolbar-btn? (:node-state %1) :canvas-move)
      :hidden #(= (:mode %1) :read)}
     {:elem :btn
      :id "selection-mode"
      :icon "far fa-expand"
      :title "Selection mode"
      :on-click #(swap! (:node-state %1) assoc :canvas-mode :canvas-select)
      :active #(active-toolbar-btn? (:node-state %1) :canvas-select)
      :hidden #(= (:mode %1) :read)}]
    [{:elem :btn
      :id "copy"
      :icon "far fa-copy"
      :title "Copy"
      :on-click #(omt/copy-units-to-clipboard (:component %1) (:unit-tree %1) (:app-state %1))
      :disabled #(not (omt/has-selection? (:unit-tree %1) (:app-state %1)))
      :hidden #(= (:mode %1) :read)}
     {:elem :btn
      :id "paste"
      :icon "far fa-paste"
      :title "Paste - click then click on canvas"
      :on-click #(swap! (:node-state %1) assoc :canvas-mode :canvas-paste)
      :active #(active-toolbar-btn? (:node-state %1) :canvas-paste)
      :disabled #(omt/is-clipboard-empty? (:unit-tree %1) (:app-state %1))
      :hidden #(= (:mode %1) :read)}]]

  :orgpad/uedit-toolbar nil
  })
