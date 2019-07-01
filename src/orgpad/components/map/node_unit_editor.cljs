(ns orgpad.components.map.node-unit-editor
  (:require-macros [orgpad.tools.colls :refer [>-]])
  (:require [rum.core :as rum]
            [orgpad.tools.rum :as trum]
            [orgpad.cycle.life :as lc]
            [orgpad.components.registry :as registry]
            [orgpad.tools.css :as css]
            [orgpad.tools.js-events :as jev]
            [orgpad.tools.geom :as geom :refer [-- ++ *c screen->canvas canvas->screen]]
            [orgpad.tools.orgpad :as ot]
            [orgpad.tools.orgpad-manipulation :as omt]
            [orgpad.tools.styles :as styles]
            [orgpad.tools.dom :as dom]
            [orgpad.tools.math :refer [normalize-range]]
            [orgpad.components.graphics.primitives-svg :as sg]
            [orgpad.components.atomic.component :as acomp]
            [orgpad.components.atomic.atom-editor :as aeditor]
            [orgpad.components.atomic.desc-editor :as deditor]
            [orgpad.components.atomic.tags-editor :as teditor]
            [goog.string :as gstring]
            [goog.string.format]
            [orgpad.components.map.utils :refer [mouse-pos set-mouse-pos! start-link
                                                 get-current-data try-deselect-unit]]
            [orgpad.tools.time :refer [now]]
            [orgpad.components.map.node :as mnode]))

;; TODO put into some config
(def ^:private quick-editor-width 420)
(def ^:private quick-editor-height 200)

(defn- node-unit-editor-style
  [prop & [quick-edit sub]]
  (let [width (max (:orgpad/unit-width prop) (if quick-edit quick-editor-width 0))
        height (max (:orgpad/unit-height prop) (if quick-edit quick-editor-height 0))
        bw (+ (:orgpad/unit-padding prop) (:orgpad/unit-border-width prop))
        w  (+ width (* 2 bw))
        h (+ height (* 2 bw))
        pos (-- (:orgpad/unit-position prop) [(/ width 2) (/ height 2)])
        sub (or sub 2)]
    (merge {:width w
            :height h
            :borderRadius (str (:orgpad/unit-corner-x prop) "px "
                               (:orgpad/unit-corner-y prop) "px")}
           (css/transform {:translate [(- (pos 0) sub) (- (pos 1) sub)]}))))

(defn- draw-link-line
  [component unit-tree parent-view local-state]
  (let [tr (parent-view :orgpad/transform)
        bbox (lc/get-global-cache component (ot/uid unit-tree) "bbox")
        ox (.-left bbox)
        oy (.-top bbox)]
    (sg/line (screen->canvas tr [(- (@local-state :link-start-x) ox)
                                 (- (@local-state :link-start-y) oy)])
             (screen->canvas tr [(- (@local-state :mouse-x) ox)
                                 (- (@local-state :mouse-y) oy)])
             {:css {:zIndex 2} :svg {:stroke "rgba(128, 128, 128, 0.5)" ;; TODO put insto some config
                                     :stroke-linecap "round"
                                     ;; :stroke-dasharray "4 3"
                                     :stroke-width 3} :key 1})))

(defn- toggle-quick-edit
  [component local-state]
  (swap! local-state update :quick-edit not)
  (.forceUpdate component))

(defn- gti-raw
  [top-class prop icon title]
  [:span {:className top-class}
   [:span (merge prop
                 {:title title
                  :className icon})]])

(def gti (partial gti-raw "right hover"))

(defn- edit-text?
  [{:keys [unit view]}]
  (case (:orgpad/view-type view)
    :orgpad/atomic-view true
    :orgpad/map-tuple-view
    (let [pos (or (:orgpad/active-unit view) 0)
          current-page (ot/get-sorted-ref unit pos)]
      (= (ot/view-type current-page) :orgpad/atomic-view))
    false))

(defn start-edit
  [component unit-tree local-state]
  (if (edit-text? unit-tree)
    (toggle-quick-edit component local-state)
    (omt/open-unit component unit-tree)))

(defn- gen-top-toolbar
  [component unit-tree parent-tree local-state uedit-local-state]
  (let [sedit (jev/make-block-propagation
               #(start-edit component unit-tree local-state))]
    [:div.tool-bar-top
     (gti {:on-mouse-down (jev/make-block-propagation (partial start-link local-state))
           :on-touch-start (jev/make-block-propagation
                            #(start-link local-state (aget % "touches" 0)))}
          "edit-link" "Make a Link")
     (gti {:on-click (jev/make-block-propagation
                      #(omt/remove-unit component
                                        {:id (ot/uid unit-tree)
                                         :view-name (ot/view-name parent-tree)
                                         :ctx-unit (ot/uid parent-tree)} local-state))}
          "edit-delete" "Delete")
     (gti {:on-click sedit}
          "edit-focus-mode" "Edit Content")
     (gti {:on-click (jev/make-block-propagation #(swap! uedit-local-state assoc :show-info? true))}
          "edit-info" "Edit Info")]))

(defn- gen-bottom-toolbar
  [component unit-tree parent-tree local-state]
  (let []
    [:div
     [:div.tool-bar-bottom
      (gti {:onClick (jev/make-block-propagation #(swap! local-state assoc :show-new-page-box true))}
           "edit-add-page-full" "Add Page")]]))

(defn- gen-view-type
  [local-state [view-type info]]
  [:div.view-type
   [:span.left [:span {:className (:orgpad/view-icon info)}]]
   [:span.left (:orgpad/view-name info)]
   [:span.right [:span {:className (if (= view-type (:selected-view-type @local-state))
                                     "edit-enabled"
                                     "edit-disbaled")
                        :onClick (jev/make-block-propagation
                                  #(swap! local-state assoc :selected-view-type view-type))}]]])

(defn- gen-view-types
  [local-state]
  (->> (dissoc (registry/get-registry) :orgpad/root-view)
       (map (partial gen-view-type local-state))))

(defn- gen-new-page-box
  [component unit-tree parent-tree local-state className]
  [:div {:className (str "new-page-box " className)}
   [:div.label "NEW PAGE:"]
   (gen-view-types local-state)
   [:div.buttons
    [:span.edit-new-ok.left.hover
     {:onClick (jev/make-block-propagation
                #(do
                   (swap! local-state assoc :show-new-page-box false)
                   (omt/new-sheet-with-type component unit-tree (:selected-view-type @local-state))))}]
    [:span.edit-new-cancel.right.hover {:onClick #(swap! local-state assoc :show-new-page-box false)}]]])

(defn- gen-toolbar
  [component unit-tree parent-tree local-state]
  (let [uedit-local-state (trum/comp->local-state component)
        top-bar (gen-top-toolbar component unit-tree parent-tree local-state uedit-local-state)
        one-page? (= (ot/refs-count unit-tree) 1)
        bottom-bar (when one-page?
                     (gen-bottom-toolbar component unit-tree parent-tree uedit-local-state))
        new-page-box (when (and (:show-new-page-box @uedit-local-state) one-page?)
                       (gen-new-page-box component unit-tree parent-tree uedit-local-state "one-page"))]
    [:div top-bar bottom-bar new-page-box]))

(defn- simple-editor
  [{:keys [view unit] :as unit-tree} height]
  (aeditor/atom-editor (:db/id unit) view (:orgpad/atom view) :quick (- height 70)))

(defn- quick-editor
  [component {:keys [view unit] :as unit-tree} height local-state]
  (let [pos (or (:orgpad/active-unit view) 0)]
    [:div.quick-editor-wrapper {:key "quick-editor"}
     [:div {:key (str "quick-editor-" (:db/id unit) "-" pos)
            :className "atomic-view"}
     (case (:orgpad/view-type view)
       :orgpad/atomic-view (simple-editor unit-tree height)
       :orgpad/map-tuple-view (simple-editor (ot/get-sorted-ref unit pos) height)
       nil)]
     [:div.quick-editor-dots {:title "Expand"
                                :onClick #(omt/open-unit component unit-tree)}
      [:span.edit-dots]]
     [:div.quick-editor-ok {:onClick #(toggle-quick-edit component local-state)}
        "OK"]]))

(defn- start-unit-move
  [app-state local-state uedit-local-state ev]
  (set-mouse-pos! (jev/touch-pos ev))
  (swap! uedit-local-state assoc :last-click-ts (now))
  (if (and (= (:mode app-state) :write)
           (= (:canvas-mode @local-state) :canvas-create-unit))
    (start-link local-state ev)
    (swap! local-state merge {:local-mode :unit-move
                              :unit-move-mode :unit
                              :quick-edit false
                              :start-mouse-x (.-clientX (jev/touch-pos ev))
                              :start-mouse-y (.-clientY (jev/touch-pos ev))
                              :mouse-x (.-clientX (jev/touch-pos ev))
                              :mouse-y (.-clientY (jev/touch-pos ev))})))

(defn- start-page-nav-move
  [local-state uedit-local-state ev]
  (set-mouse-pos! (jev/touch-pos ev))
  (swap! local-state merge {:local-mode :unit-move
                            :unit-move-mode :page-nav
                            :unit-editor-local-state uedit-local-state
                            :quick-edit false
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

(defn- resize-handle
  "Add resize-handle.
  Class suffixes are top-left, top, top-right, right, bottom-right, bottom, bottom-left, and left."
  [mode local-state]
  (let [class-name (str "resize-handle-" mode)]
    [:span
     {:class class-name :id class-name
      :onMouseDown (jev/make-block-propagation #(start-unit-resize local-state (keyword mode) %))
      :onTouchStart (jev/make-block-propagation #(start-unit-resize local-state (keyword mode)
                                                                    (aget % "touches" 0)))}]))

(defn- page-nav
  [component unit-tree prop style mode local-state]
  (let [uedit-local-state (trum/comp->local-state component)
        pos (:orgpad/unit-position prop)
        w (:orgpad/unit-width prop)
        h (:orgpad/unit-height prop)
        rel-pos (if (= (:page-nav-current @uedit-local-state) (ot/uid unit-tree)) ;; ugly hack
                  (:page-nav-pos @uedit-local-state)
                  (let [rp [(+ (/ w 2) 80) 0]]
                    (swap! uedit-local-state assoc
                           :page-nav-current (ot/uid unit-tree)
                           :page-nav-pos rp)
                    rp))
        page-style (css/transform {:translate (++ pos rel-pos)})
        new-page-box (when (:show-new-page-box @uedit-local-state)
                       (gen-new-page-box component unit-tree nil uedit-local-state "multi-page"))]
    [:div {:id "unit-editor-page-nav"
           :className (str "page-nav " (name mode))
           :onMouseDown (jev/make-block-propagation
                         #(start-page-nav-move local-state uedit-local-state %))
           :onDoubleClick jev/block-propagation
           :onTouchStart (jev/make-block-propagation
                          #(start-page-nav-move local-state uedit-local-state
                                                (aget % "touches" 0)))
           :style page-style}
     [:span.prev.edit-page-prev.hover1 {:title "Previous"
                                        :on-click #(omt/switch-active-sheet component unit-tree -1)}]
     [:span.nums [:span (ot/sheets-to-str unit-tree)]]
     [:span.next.edit-page-next.hover1 {:title "Next"
                                        :on-click #(omt/switch-active-sheet component unit-tree 1)}]
     (when (= mode :write)
       (gti-raw "add-page hover" {:onClick #(swap! uedit-local-state assoc :show-new-page-box true)}
                "edit-add-page" "Add Page"))
     (when (= mode :write)
       (gti-raw "del-page hover" {:onClick #(omt/remove-active-sheet component unit-tree)}
                "edit-del-page" "Delete Page"))
     new-page-box]))

(defn- gen-info
  [component sel-unit-tree prop unit-tree mode local-state]
  (let [uedit-local-state (trum/comp->local-state component)
        width (:orgpad/unit-width prop)
        bw (+ (:orgpad/unit-padding prop) (:orgpad/unit-border-width prop))
        style {:width (+ width bw)
               :top (- -68 bw)}
        view (:view sel-unit-tree)]
    [:div.info-editor {:style style
                       :onMouseDown #(.stopPropagation %)
                       :onMouseUp #(.stopPropagation %)}
     [:div
      [:span.fas.fa-times.cancel {:on-click #(swap! uedit-local-state assoc :show-info? false)}]
      (when (= mode :write)
        (deditor/desc-editor (ot/uid sel-unit-tree) view (:orgpad/desc view)))
      (when (= mode :write)
        (teditor/tags-editor (ot/uid sel-unit-tree) view (:orgpad/tags view)))
      (when (= mode :read)
        (acomp/render-info view))]]))

(defn node-unit-editor
  [component {:keys [view] :as unit-tree} app-state local-state]
  (let [[sel-unit-tree prop parent-view selected?] (get-current-data unit-tree local-state)
        style (if selected? (node-unit-editor-style prop (:quick-edit @local-state)) {})
        qedit? (:quick-edit @local-state)
        uedit-local-state (trum/comp->local-state component)
        show-info? (:show-info? @uedit-local-state)]
    [:div {:key "node-unit-editor"
           :ref "unit-editor-node"
           :style (if selected? {:display "block"} {:display "none"})}
     [:div {:className "map-view-unit-selected"
            :style (merge style (when qedit? {:background-color "white"}))
            :onDoubleClick jev/block-propagation
            :onClick #(when (< (- (now) (:last-click-ts @uedit-local-state)) 250)
                        (start-edit component sel-unit-tree local-state))
            :onMouseUp (when selected?
                         (partial try-deselect-unit component (ot/uid unit-tree)
                                  (ot/uid sel-unit-tree) local-state))
            :onMouseDown (when selected?
                           (if qedit?
                             jev/stop-propagation
                             (jev/make-block-propagation #(start-unit-move app-state local-state
                                                                           uedit-local-state %))))
            :onTouchStart (when selected?
                            (if qedit?
                              jev/stop-propagation
                              (jev/make-block-propagation #(start-unit-move app-state local-state
                                                                            uedit-local-state
                                                                            (aget % "touches" 0)))))}
      (if qedit?
        (quick-editor component sel-unit-tree (:height style) local-state)
        (for [mode ["top-left" "top" "top-right" "right" "bottom-right" "bottom" "bottom-left" "left"]]
          (resize-handle mode local-state)))
      (when (not qedit?)
        (gen-toolbar component sel-unit-tree unit-tree local-state))
      (when (and (not qedit?) show-info?)
        (gen-info component sel-unit-tree prop unit-tree :write local-state))]
     (when (and (not qedit?) selected? (> (ot/refs-count sel-unit-tree) 1))
       (page-nav component sel-unit-tree prop style :write local-state))
     (when (= (@local-state :local-mode) :make-link)
       (draw-link-line component unit-tree parent-view local-state))]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Read mode unit manipulator ;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn gen-manipulator-toolbar
  [component sel-unit-tree unit-tree prop local-state uedit-local-state]
  (let [pos (:orgpad/unit-position prop)
        w (:orgpad/unit-width prop)
        h (:orgpad/unit-height prop)
        style (css/transform {:translate (++ pos [(/ w 2) (- (/ h 2))]) :scale 0.7})]
    [:div.manipulator-toolbar {:style style}
     (gti {:on-click #(omt/open-unit component sel-unit-tree)} "read-focus" "Open")
     (gti {:on-click #(swap! uedit-local-state assoc :show-info? true)}
          "edit-info" "Show Info")]))

(defn node-unit-manipulator
  [component {:keys [view] :as unit-tree} app-state local-state]
  (let [[sel-unit-tree prop parent-view selected?] (get-current-data unit-tree local-state)
        style (if selected?
                (node-unit-editor-style prop false)
                {})
        uedit-local-state (trum/comp->local-state component)]
    [:div {:key "node-unit-editor"
           :ref "unit-editor-node"
           :style (if selected? {:display "block"} {:display "none"})}
     [:div {:className "map-view-unit-selected simple"
            :style style
            :key 0}
      (when (:show-info? @uedit-local-state)
        (gen-info component sel-unit-tree prop unit-tree :read local-state))]
     (gen-manipulator-toolbar component sel-unit-tree unit-tree prop local-state uedit-local-state)
     (when (and selected? (> (ot/refs-count sel-unit-tree) 1))
       (page-nav component sel-unit-tree prop style :read local-state))]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Unit interest highlight ;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def right-border [280 0]) ;; right sidebar - TODO: determine its size in more generic way

(defn- get-coord
  [prop cidx delta bb-min bb-max]
  (-> prop :orgpad/unit-position (get cidx) (max (+ (bb-min cidx) delta)) (min (- (bb-max cidx) delta))))

(defn- determine-position
  [{:as prop :keys [orgpad/unit-width orgpad/unit-height]} [bb-min bb-max]]
  (let [bw (* 2 (ot/unit-border-width prop))
        x (get-coord prop 0 (+ (/ unit-width 2) bw) bb-min bb-max)
        y (get-coord prop 1 (+ (/ unit-height 2) bw) bb-min bb-max)]
    [x y]))

(defn- start-at-origin
  [[mi ma]]
  [[0 0] (-- ma mi right-border)])

(defn- change-prop
  [props prop]
  (-> not
      (comp
       (partial ot/props-pred-no-ctx (:orgpad/view-name prop)
                (:orgpad/view-type prop) (:orgpad/type prop)))
      (filter props)
      (conj prop)))

(defn node-interest
  [component {:keys [view] :as unit-tree} app-state local-state [child-unit-tree prop]]
  (let [global-cache (lc/get-global-cache component)
        screen-bbox (-> (aget global-cache (ot/uid unit-tree) "bbox")
                        dom/dom-bb->bb start-at-origin
                        (->> (geom/screen-bb->canvas-bb (:orgpad/transform view))))
        bb (->> child-unit-tree ot/uid (ot/unit-bb-by-vprop prop))
        visible? (geom/bbs-intersect? screen-bbox (:bb bb))
        hpos (determine-position prop screen-bbox)
        prop' (assoc prop :orgpad/unit-position hpos)
        style (if visible?
                (node-unit-editor-style prop false 0)
                (node-unit-editor-style prop' false 0))]
    (js/console.log "node-interest" screen-bbox (:bb bb) visible?)
    [:div
     (when-not visible?
       (mnode/map-unit (update child-unit-tree :props change-prop prop')
                       (assoc app-state :mode :read :selections nil)
                       component (:orgpad/view-name view) (ot/uid unit-tree) local-state))
     [:div {:className "map-view-unit-interested"
            :style style
            :key (str "interest-" (ot/uid child-unit-tree))}

      ]]))

(defn node-unit-interest
  [component unit-tree app-state local-state]
  [:div
   (mapv (partial node-interest component unit-tree app-state local-state)
         (get-in app-state [:interests (ot/uid unit-tree)]))])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Multiselection editor ;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn compute-bb
  [component unit-tree selection]
  (let [id (ot/uid unit-tree)
        global-cache (lc/get-global-cache component)
        screen-bbox (dom/dom-bb->bb (aget global-cache id "bbox"))
        bbs (map :bb (ot/child-bbs unit-tree selection))
        bb (geom/bbs-bbox bbs)]
    bb))

(defn- start-units-move
  [unit-tree selection local-state ev]
  (set-mouse-pos! (jev/touch-pos ev))
  (swap! local-state merge {:local-mode :units-move
                            :quick-edit false
                            :selected-units [unit-tree selection]
                            :start-mouse-x (.-clientX (jev/touch-pos ev))
                            :start-mouse-y (.-clientY (jev/touch-pos ev))
                            :mouse-x (.-clientX (jev/touch-pos ev))
                            :mouse-y (.-clientY (jev/touch-pos ev))}))

(defn- start-links
  [unit-tree selection local-state ev]
  (set-mouse-pos! (jev/touch-pos ev))
  (start-link local-state ev)
  (swap! local-state merge {:local-mode :make-links
                            :selected-units [unit-tree selection]}))

(defn- gen-nodes-top-toolbar
  [component unit-tree local-state selection]
  [:div.tool-bar-top
   (gti {:on-mouse-down (jev/make-block-propagation (partial start-links unit-tree selection local-state))
         :on-touch-start (jev/make-block-propagation
                          #(start-links unit-tree selection local-state (aget % "touches" 0)))}
        "edit-link" "Make a Link")
   (gti {:on-click #(omt/remove-units component
                                      {:pid (ot/uid unit-tree)
                                       :view-name (ot/view-name unit-tree)}
                                      selection)}
        "edit-delete" "Delete")])

(defn- gen-nodes-toolbar
  [component unit-tree  local-state selection]
  (let [top-bar (gen-nodes-top-toolbar component unit-tree local-state selection)]
    [:div top-bar]))

(def ^:private bb-border [5 5])

(defn nodes-unit-editor
  [component {:keys [view] :as unit-tree} app-state local-state]
  (let [[sel-unit-tree prop parent-view _] (get-current-data unit-tree local-state)
        selection (get-in app-state [:selections (ot/uid unit-tree)])
        bb (compute-bb component unit-tree selection)
        pos (-- (bb 0) bb-border)
        [width height] (++ (-- (bb 1) (bb 0)) bb-border)
        style (merge {:width width
                      :height height
                      :zIndex (if (:ctrl-key @local-state) -1 1)}
                     (css/transform {:translate [(- (pos 0) 2) (- (pos 1) 2)]}))]
    [:div {:key "node-unit-editor" :ref "unit-editor-node"}
     [:div {:className "map-view-unit-selected nodes"
            :style style
            :key 0
            :onMouseDown (jev/make-block-propagation #(start-units-move unit-tree selection local-state %))
            :onTouchStart (jev/make-block-propagation #(start-units-move unit-tree selection local-state
                                                                         (aget % "touches" 0)))}
      (gen-nodes-toolbar component unit-tree local-state selection)]
     (when (= (@local-state :local-mode) :make-links)
       (draw-link-line component unit-tree parent-view local-state))]))
