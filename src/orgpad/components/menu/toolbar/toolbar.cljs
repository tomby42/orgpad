(ns ^{:doc "Toolbar component"}
  orgpad.components.menu.toolbar
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
            [goog.string :as gstring]
            [goog.string.format]))

(defn- add-left-button
  [title icon text on-mouse-down active]
  (let [button-class (str "lft-btn" (when active " active"))]
    (if icon
      [:span
        {:className button-class
         :title title
         :onMouseDown on-mouse-down }
         [:i { :className (str "fa " icon " fa-lg fa-fw") }]
         (when text [:span.btn-icon-text text])]
      [:span
        {:className button-class
         :title title
         :onMouseDown on-mouse-down }
         (when text [:span.btn-text text])])))

(defn- add-test-roll-button []
  [:span.lft-roll
    [:span.lft-roll-btn
     {:title "Roll test"
      :onMouseDown #(js/console.log "Roll button pressed")}
      [:i { :className "fa fa-plus-circle fa-lg fa-fw" }]
      [:span.btn-icon-text "Roll button"]
      [:i { :className "fa fa-caret-down" }]]
    [:span.roll-items
      [:span.roll-item
       {:title "Roll item 1"
        :onMouseDown #(js/console.log "Roll item 1 pressed")}
        [:i.fa.fa-columns.fa-lg.fa-fw]
        [:span.roll-icon-label "Notebook view"]]
      [:span.roll-item
       {:title "Roll item 2"
        :onMouseDown #(js/console.log "Roll item 2 pressed")}
        [:i.fa.fa-window-restore.fa-lg.fa-fw]
        [:span.roll-icon-label "Map view"]]
      [:span.roll-item
       {:title "Roll item 3"
        :onMouseDown #(js/console.log "Roll item 3 pressed")}
        [:span.roll-label "Very long test"]]
      [:span.roll-item
       {:title "Roll item 4"
        :onMouseDown #(js/console.log "Roll item 4 pressed")}
        [:span.roll-label "A"]]
      [:span.roll-item
       {:title "Roll item 5"
        :onMouseDown #(js/console.log "Roll item 5 pressed")}
        [:span.roll-label "B"]]
      ]])

(defn- add-notebook-manipulators
  [component {:keys [unit view] :as unit-tree}]
  [:span
    [:span.lft-sep]
     [:span.lft-btn
      {:title "Previous page"
       :onMouseDown #(omt/switch-active-sheet component unit-tree -1) }
      [:i.fa.fa-arrow-left.fa-lg.fa-fw]]
     [:span.lft-btn
      {:title "Next page"
       :onMouseDown #(omt/switch-active-sheet component unit-tree 1) }
      [:i.fa.fa-arrow-right.fa-lg.fa-fw]]
     [:span.lft-text (apply gstring/format "%d/%d" (ot/get-sheet-number unit-tree))]
     [:span.lft-btn
      {:title "Add page"
       :onMouseDown #(omt/new-sheet component unit-tree) }
      [:i.fa.fa-plus-circle.fa-lg.fa-fw]]
     [:span.lft-btn
      {:title "Remove page"
       :onMouseDown #(omt/remove-active-sheet component unit-tree) }
      [:i.fa.fa-minus-circle.fa-lg.fa-fw]]
     (let [ ac-unit-tree (ot/active-child-tree unit view)
            ac-view-type (ot/view-type ac-unit-tree)
            class-sheet (str "lft-btn" (when (= ac-view-type :orgpad/atomic-view) " active"))
            class-map (str "lft-btn" (when (= ac-view-type :orgpad/map-view) " active"))]
       (list
         [:span
          {:className class-sheet
           :title "Sheet"
           :onMouseDown #(omt/change-view-type component ac-unit-tree :orgpad/atomic-view) }
           [:i.fa.fa-file-text-o.fa-lg.fa-fw]]
         [:span
          {:className class-map
           :title "Map"
           :onMouseDown #(omt/change-view-type component ac-unit-tree :orgpad/map-view) }
          [:i.fa.fa-window-restore.fa-lg.fa-fw]]))])

(defn- add-view-buttons
  [component unit-tree]
  (let [view-type (ot/view-type unit-tree)
        class-notebook (str "lft-btn" (when (= view-type :orgpad/map-tuple-view) " active"))
        class-map (str "lft-btn" (when (= view-type :orgpad/map-view) " active"))]
    [:span
     [:span
      { :className class-notebook
       :title "Notebook"
       :onMouseDown #(omt/change-view-type component unit-tree :orgpad/map-tuple-view) }
      [:i.fa.fa-columns.fa-lg.fa-fw]]
     [:span
      { :className class-map
       :title "Map"
       :onMouseDown #(omt/change-view-type component unit-tree :orgpad/map-view) }
      [:i.fa.fa-window-restore.fa-lg.fa-fw]]
     (when (= view-type :orgpad/map-tuple-view)
      (add-notebook-manipulators component unit-tree))
     [:span.lft-sep]]))

(defn render-unit-editor-toolbar
  [component unit-tree app-state local-state]
  [:span.toolbar
    [:span.lft-btn
      { :title "Link"
        :onMouseDown (jev/make-block-propagation #(omt/start-link local-state %))
        :onTouchStart (jev/make-block-propagation #(omt/start-link local-state (aget % "touches" 0)))}
     [:i.fa.fa-link.fa-lg.fa-fw]]
    [:span.lft-btn
      { :title "Edit"
        :onMouseDown jev/block-propagation
        :onMouseUp (jev/make-block-propagation #(omt/open-unit component unit-tree))}
     [:i.fa.fa-pencil-square-o.fa-lg.fa-fw]]
    [:span.lft-sep]
    (add-view-buttons component unit-tree)

    [:span.rt-btn
      { :title "Remove"
        :onMouseDown #(omt/remove-unit component (ot/uid unit-tree))}
     [:i.fa.fa-remove.fa-lg.fa-fw]]])

(defn- render-map-tools
  [local-state-atom]
  (let [canvas-mode (:canvas-mode @local-state-atom)
        class-create (str "lft-btn" (when (= canvas-mode :canvas-create-unit) " active"))
        class-move (str "lft-btn" (when (= canvas-mode :canvas-move) " active"))
        class-select (str "lft-btn" (when (= canvas-mode :canvas-select) " active"))]
    [:span
      [:span
       {:className class-create
        :title "Unit creation mode"
        :onClick #(swap! local-state-atom assoc :canvas-mode :canvas-create-unit)}
        [:i.fa.fa-plus.fa-lg.fa-fw]]
      [:span
       {:className class-move
        :title "Moving mode"
        :onClick #(swap! local-state-atom assoc :canvas-mode :canvas-move)}
        [:i.fa.fa-arrows.fa-lg.fa-fw]]
      [:span
       {:className class-select
        :title "Selection mode"
        :onClick #(swap! local-state-atom assoc :canvas-mode :canvas-select)}
        [:i.fa.fa-crop.fa-lg.fa-fw]]
      [:span.lft-sep]]))

(defn- render-copy-tools
  [component unit-tree app-state local-state-atom]
  (let [class-paste (str "lft-btn" (when (= (:local-mode @local-state-atom) :canvas-paste) " active"))]
    [:span
      [:span.lft-btn
       {:title "Copy"
        :onClick #(omt/copy-units-to-clipboard component unit-tree app-state)}
        [:i.fa.fa-copy.fa-lg.fa-fw]]
      [:span
       {:className class-paste
        :title "Paste"
        :onMouseDown #(swap! local-state-atom assoc :local-mode :canvas-paste)}
        [:i.fa.fa-paste.fa-lg.fa-fw]]
      [:span.lft-sep]]))

(defn render-app-toolbar
  [component unit-tree app-state local-state-atom]
  [:div.map-local-menu
   {:onMouseDown jev/block-propagation
    :onTouchStart jev/block-propagation }
    (render-map-tools local-state-atom)
    (render-copy-tools component unit-tree app-state local-state-atom)
    (add-view-buttons component unit-tree)
    (add-left-button "Test" "fa-plus" "Another test" #(js/console.log "Test") true )
    (add-left-button "Test" "fa-chevron-right" nil #(js/console.log "Test") nil)
    (add-left-button "Test" nil "Just text" #(js/console.log "Test") true )
    (add-test-roll-button)
    ])
