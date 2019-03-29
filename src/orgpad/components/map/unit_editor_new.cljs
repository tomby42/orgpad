(ns orgpad.components.map.unit-editor-new
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
            [orgpad.components.atomic.atom-editor :as aeditor]
            [goog.string :as gstring]
            [goog.string.format]
            [orgpad.components.map.utils :refer [mouse-pos set-mouse-pos! start-link]]))

;; TODO put into some config
(def ^:private quick-editor-width 420)
(def ^:private quick-editor-height 200)

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
        default-style (-> :orgpad/map-view registry/get-component-info
                          :orgpad/child-props-default style-type)]
    [sel-unit (merge default-style sel-style sel-prop)]))

(defn- node-unit-editor-style
  [prop & [quick-edit]]
  (let [width (max (:orgpad/unit-width prop) (if quick-edit quick-editor-width 0))
        height (max (:orgpad/unit-height prop) (if quick-edit quick-editor-height 0))
        bw (+ (:orgpad/unit-padding prop) (:orgpad/unit-border-width prop))
        w  (+ width (* 2 bw))
        h (+ height (* 2 bw))
        pos (-- (:orgpad/unit-position prop) [(/ width 2) (/ height 2)])]
    (merge {:width w
            :height h
            :borderRadius (str (:orgpad/unit-corner-x prop) "px "
                               (:orgpad/unit-corner-y prop) "px")}
           (css/transform {:translate [(- (pos 0) 2) (- (pos 1) 2)]}))))

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

(defn- get-current-data
  [unit-tree local-state]
  (if (@local-state :selected-unit)
    (let [[old-unit old-prop parent-view] (@local-state :selected-unit)
          [sel-unit-tree prop] (selected-unit-prop unit-tree (ot/uid old-unit)
                                                   (old-prop :db/id) (:orgpad/view-type old-prop))]
      (if (and prop sel-unit-tree)
        [sel-unit-tree prop parent-view true]
        [{} {} {} false]))
    [{} {} {} false]))

(defn try-deselect-unit
  [component pid uid local-state ev]
  (when (and uid (.-ctrlKey ev))
    (swap! local-state assoc :selected-unit nil))
  (lc/transact! component [[:orgpad.units/select {:pid pid
                                                  :toggle? (.-ctrlKey ev)
                                                  :uid uid}]]))

(defn- gti-raw
  [top-class prop icon title]
  [:span {:className top-class}
   [:span (merge prop
                 {:title title
                  :className icon})]])

(def gti (partial gti-raw "right hover"))

(defn- toggle-quick-edit
  [local-state]
  (swap! local-state update :quick-edit not))

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
    (toggle-quick-edit local-state)
    (omt/open-unit component unit-tree)))

 (defn- gen-top-toolbar
  [component unit-tree parent-tree local-state]
  (let [sedit (jev/make-block-propagation
               #(start-edit component unit-tree local-state))]
    [:div.tool-bar-top
     (gti {:on-click #(omt/remove-unit component
                                       {:id (ot/uid unit-tree)
                                        :view-name (ot/view-name parent-tree)
                                        :ctx-unit (ot/uid parent-tree)} local-state)}
          "edit-delete" "Delete")
     (gti {:on-mouse-down sedit
           :on-touch-start sedit}
          "edit-focus-mode" "Edit Content")
     (gti {}
          "edit-info" "Edit Info")
     (gti {:on-mouse-down (jev/make-block-propagation (partial start-link local-state))
           :on-touch-start (jev/make-block-propagation
                            #(start-link local-state (aget % "touches" 0)))}
          "edit-link" "Make a Link")]))

(defn- gen-bottom-toolbar
  [component unit-tree parent-tree local-state]
  (let []
    [:div
     [:div.tool-bar-bottom
      (gti {:onClick #(swap! local-state assoc :show-new-page-box true)}
           "edit-add-page-full" "Add Page")]]))

(defn- gen-view-type
  [local-state [view-type info]]
  [:div.view-type
   [:span.left [:span {:className (:orgpad/view-icon info)}]]
   [:span.left (:orgpad/view-name info)]
   [:span.right [:span {:className (if (= view-type (:selected-view-type @local-state))
                                     "edit-enabled"
                                     "edit-disbaled")
                        :onClick #(swap! local-state assoc :selected-view-type view-type)}]]])

(defn- gen-view-types
  [local-state]
  (->> (dissoc (registry/get-registry) :orgpad/root-view)
       (map (partial gen-view-type local-state))))

(defn- gen-new-page-box
  [component unit-tree parent-tree local-state]
  [:div.new-page-box
   [:div.label "NEW PAGE:"]
   (gen-view-types local-state)
   [:div.buttons
    [:span.edit-new-ok.left.hover
     {:onClick #(do
                  (swap! local-state assoc :show-new-page-box false)
                  (omt/new-sheet-with-type component unit-tree (:selected-view-type @local-state)))}]
    [:span.edit-new-cancel.right.hover {:onClick #(swap! local-state assoc :show-new-page-box false)}]]])

(defn- gen-toolbar
  [component unit-tree parent-tree local-state]
  (let [uedit-local-state (trum/comp->local-state component)
        top-bar (gen-top-toolbar component unit-tree parent-tree local-state)
        bottom-bar (when (= (ot/refs-count unit-tree) 1)
                     (gen-bottom-toolbar component unit-tree parent-tree uedit-local-state))
        new-page-box (when (:show-new-page-box @uedit-local-state)
                       (gen-new-page-box component unit-tree parent-tree uedit-local-state))]
    [:div top-bar bottom-bar new-page-box]))

(defn- quick-editor
  [component {:keys [view unit] :as unit-tree} height local-state]
  (let [pos (or (:orgpad/active-unit view) 0)]
    [:div.quick-editor-wrapper
     [:div {:key (str "quick-editor-" (:db/id unit) "-" pos)
            :className "atomic-view"}
     (case (:orgpad/view-type view)
       :orgpad/atomic-view (aeditor/atom-editor (:db/id unit) view (:orgpad/atom view) :quick (- height 70))
       :orgpad/map-tuple-view (quick-editor component (ot/get-sorted-ref unit pos) height local-state)
       nil)]
     (when (= (:orgpad/view-type view) :orgpad/map-tuple-view)
       [:div.edit-dots.quick-editor-dots {:title "Expand"
                                          :onClick #(omt/open-unit component unit-tree)}])
     (when (= (:orgpad/view-type view) :orgpad/map-tuple-view)
       [:div.edit-ok.quick-editor-ok {:onClick #(toggle-quick-edit local-state)}])]))

(defn- start-unit-move
  [app-state local-state ev]
  (set-mouse-pos! (jev/touch-pos ev))
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
  [component unit-tree prop style local-state]
  (let [uedit-local-state (trum/comp->local-state component)
        pos (:orgpad/unit-position prop)
        w (:orgpad/unit-width prop)
        h (:orgpad/unit-height prop)
        page-style (css/transform {:translate (++ pos (:page-nav-pos @uedit-local-state))})]
    [:div.page-nav {:id "unit-editor-page-nav"
                    :on-mouse-down (jev/make-block-propagation
                                    #(start-page-nav-move local-state uedit-local-state %))
                    :on-touch-start (jev/make-block-propagation
                                     #(start-page-nav-move local-state uedit-local-state
                                                           (aget % "touches" 0)))
                    :style page-style}
     [:span.prev.edit-page-prev.hover1 {:title "Previous"
                                        :on-click #(omt/switch-active-sheet component unit-tree -1)}]
     [:span.nums [:span (ot/sheets-to-str unit-tree)]]
     [:span.next.edit-page-next.hover1 {:title "Next"
                                        :on-click #(omt/switch-active-sheet component unit-tree 1)}]
     (gti-raw "add-page hover" {:onClick #(swap! uedit-local-state assoc :show-new-page-box true)}
              "edit-add-page" "Add Page")
     (gti-raw "del-page hover" {:onClick #(omt/remove-active-sheet component unit-tree)}
              "edit-del-page" "Delete Page")]))

(defn node-unit-editor-new
  [component {:keys [view] :as unit-tree} app-state local-state]
  (let [[sel-unit-tree prop parent-view selected?] (get-current-data unit-tree local-state)]
    (let [style (if selected?
                  (node-unit-editor-style prop (:quick-edit @local-state))
                  {})
          qedit? (:quick-edit @local-state)]
      [:div {:key "node-unit-editor"
             :ref "unit-editor-node"
             :style (if selected? {:display "block"} {:display "none"})}
       [:div {:className "map-view-unit-selected"
              :style (merge style (when qedit? {:background-color "white"}))
              :key 0
              :onDoubleClick jev/block-propagation
              :onMouseUp (when selected?
                           (partial try-deselect-unit component (ot/uid unit-tree)
                                    (ot/uid sel-unit-tree) local-state))
              :onMouseDown (when selected?
                             (if qedit?
                               jev/stop-propagation
                               (jev/make-block-propagation #(start-unit-move app-state local-state %))))
              :onTouchStart (when selected?
                              (if qedit?
                                jev/stop-propagation
                                (jev/make-block-propagation #(start-unit-move app-state local-state
                                                                              (aget % "touches" 0)))))}
        (if qedit?
          (quick-editor component sel-unit-tree (:height style) local-state)
          (for [mode ["top-left" "top" "top-right" "right" "bottom-right" "bottom" "bottom-left" "left"]]
            (resize-handle mode local-state)))
        (when (not qedit?)
          (gen-toolbar component sel-unit-tree unit-tree local-state))]
       (when (and (not qedit?) selected? (> (ot/refs-count sel-unit-tree) 1))
         (page-nav component sel-unit-tree prop style local-state))
       (when (= (@local-state :local-mode) :make-link)
         (draw-link-line component unit-tree parent-view local-state))])))

;; Multiselection editor

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
     (gti {:on-click #(omt/remove-units component
                                        {:pid (ot/uid unit-tree)
                                         :view-name (ot/view-name unit-tree)}
                                        selection)}
          "edit-delete" "Delete")
     (gti {:on-mouse-down (jev/make-block-propagation (partial start-links unit-tree selection local-state))
           :on-touch-start (jev/make-block-propagation
                            #(start-links unit-tree selection local-state (aget % "touches" 0)))}
          "edit-link" "Make a Link")])

(defn- gen-nodes-toolbar
  [component unit-tree  local-state selection]
  (let [top-bar (gen-nodes-top-toolbar component unit-tree local-state selection)]
    [:div top-bar]))

(defn nodes-unit-editor-new
  [component {:keys [view] :as unit-tree} app-state local-state]
  (let [[sel-unit-tree prop parent-view _] (get-current-data unit-tree local-state)
        selection (get-in app-state [:selections (ot/uid unit-tree)])
        bb (compute-bb component unit-tree selection)
        pos (bb 0)
        [width height] (-- (bb 1) (bb 0))
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
                                                                         (aget % "touches" 0)))
            }
      (gen-nodes-toolbar component unit-tree local-state selection)]
     (when (= (@local-state :local-mode) :make-links)
       (draw-link-line component unit-tree parent-view local-state))]))
