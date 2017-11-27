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

;; input format for toolbar
;; two lists, one for left-aligned buttons, one for right-aligned buttons
;;
;; each list contains one element for each group of buttons
;;
;; each group of buttons is represented by a nested list
;;
;; each button is represented by the following map
;;  {:type (:btn|:roll|:text) 
;;   :id              ...   identificator
;;   :title           ...   tooltip hint
;;   :icon            ...   font-awesome style name or nil for no icon
;;   :label           ...   displayed label or nil for no label
;;   :on-mouse-down   ...   function on mouse down
;;   :active          ...   function returning true/false whether button should be active, possibly nil
;;
;;   for :roll only
;;   :roll-items      ...   list of all roll items
;;  }
;;
;; each roll item is represented by the following map
;;  {:id              ...   identificator
;;   :title           ...   tooltip hint
;;   :icon            ...   font-awesome style name or nil for no icon
;;   :label           ...   displayed label or nil for no label
;;   :on-mouse-down   ...   function on mouse down
;;   :active          ...   function returning true/false whether button should be active, possibly nil
;;  }
;;
;; all used functions 

(defn- toggle-open-state
  [open clicked-roll]
  (if (= open clicked-roll) nil clicked-roll))

(defn- wrap-toolbar-action
  [open f]
  (reset! open nil)
  (f))

(defn- add-button
  [open params alignment {:keys [id title icon label on-mouse-down active]}]
  (let [is-active (when active (active params))
        button-class (str (if (= alignment :left) "lft-btn" "rt-btn") (when is-active " active"))
        label-class (if icon "btn-icon-label" "btn-label")]
    [:span
      {:key id
       :className button-class
       :title title
       :onMouseDown #(wrap-toolbar-action open (fn [] (on-mouse-down params))) }
       (when icon [:i { :className (str icon " fa-lg fa-fw") }])
       (when label [:span { :className label-class } label])]))
 
(defn- gen-roll-item
  [open params {:keys [id title icon label on-mouse-down active]}]
  (let [is-active (when active (active params))
        label-class (if icon "roll-icon-label" "roll-label")]
    [:span.roll-item
      {:key id
       :title title
       :onMouseDown #(wrap-toolbar-action open (fn [] (on-mouse-down params))) }
       (when icon [:i { :className (str icon " fa-lg fa-fw") }])
       (when label [:span { :className label-class } label])]))

(defn- add-roll
  [open params alignment {:keys [id title icon label active roll-items] :as data}]
  (let [is-active (when active (active params))
        roll-class (if (= alignment :left) "lft-roll" "rt-roll")
        button-class (str (if (= alignment :left) "lft-roll-btn" "rt-roll-btn") (when is-active " active"))
        label-class (if icon "btn-icon-label" "btn-label")]
    [:span {:className roll-class :key id}
      [:span 
       {:className button-class
        :title title
        :onMouseDown (jev/make-block-propagation #(swap! open toggle-open-state id))}
        (when icon [:i { :className (str icon " fa-lg fa-fw") }])
        (when label [:span { :className label-class } label])
        [:i { :className "fa fa-caret-down" }]]
      (when (= @open id)
        [:span.roll-items
          (map (partial gen-roll-item open params) roll-items)])]))

(defn- add-notebook-manipulators
  [component {:keys [unit view] :as unit-tree}]
  [:span
    [:span.lft-sep]
     [:span.lft-btn
      {:title "Previous page"
       :onMouseDown #(omt/switch-active-sheet component unit-tree -1) }
      [:i.far.fa-arrow-left.fa-lg.fa-fw]]
     [:span.lft-btn
      {:title "Next page"
       :onMouseDown #(omt/switch-active-sheet component unit-tree 1) }
      [:i.far.fa-arrow-right.fa-lg.fa-fw]]
     [:span.lft-text (apply gstring/format "%d/%d" (ot/get-sheet-number unit-tree))]
     [:span.lft-btn
      {:title "Add page"
       :onMouseDown #(omt/new-sheet component unit-tree) }
      [:i.far.fa-plus-circle.fa-lg.fa-fw]]
     [:span.lft-btn
      {:title "Remove page"
       :onMouseDown #(omt/remove-active-sheet component unit-tree) }
      [:i.far.fa-minus-circle.fa-lg.fa-fw]]
     (let [ ac-unit-tree (ot/active-child-tree unit view)
            ac-view-type (ot/view-type ac-unit-tree)
            class-sheet (str "lft-btn" (when (= ac-view-type :orgpad/atomic-view) " active"))
            class-map (str "lft-btn" (when (= ac-view-type :orgpad/map-view) " active"))]
       (list
         [:span
          {:className class-sheet
           :title "Sheet"
           :onMouseDown #(omt/change-view-type component ac-unit-tree :orgpad/atomic-view) }
           [:i.far.fa-file-alt.fa-lg.fa-fw]]
         [:span
          {:className class-map
           :title "Map"
           :onMouseDown #(omt/change-view-type component ac-unit-tree :orgpad/map-view) }
          [:i.far.fa-share-alt.fa-lg.fa-fw]]))])

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
      [:i.far.fa-book.fa-lg.fa-fw]]
     [:span
      { :className class-map
       :title "Map"
       :onMouseDown #(omt/change-view-type component unit-tree :orgpad/map-view) }
      [:i.far.fa-share-alt.fa-lg.fa-fw]]
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
     [:i.far.fa-link.fa-lg.fa-fw]]
    [:span.lft-btn
      { :title "Edit"
        :onMouseDown jev/block-propagation
        :onMouseUp (jev/make-block-propagation #(omt/open-unit component unit-tree))}
     [:i.far.fa-edit.fa-lg.fa-fw]]
    [:span.lft-sep]
    (add-view-buttons component unit-tree)

    [:span.rt-btn
      { :title "Remove"
        :onMouseDown #(omt/remove-unit component (ot/uid unit-tree))}
     [:i.far.fa-trash-alt.fa-lg.fa-fw]]])

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
        [:i.far.fa-plus-square.fa-lg.fa-fw]]
      [:span
       {:className class-move
        :title "Moving mode"
        :onClick #(swap! local-state-atom assoc :canvas-mode :canvas-move)}
        [:i.far.fa-arrows.fa-lg.fa-fw]]
      [:span
       {:className class-select
        :title "Selection mode"
        :onClick #(swap! local-state-atom assoc :canvas-mode :canvas-select)}
        [:i.far.fa-expand.fa-lg.fa-fw]]
      [:span.lft-sep]]))

(defn- render-copy-tools
  [component unit-tree app-state local-state-atom]
  (let [class-paste (str "lft-btn" (when (= (:local-mode @local-state-atom) :canvas-paste) " active"))]
    [:span
      [:span.lft-btn
       {:title "Copy"
        :onClick #(omt/copy-units-to-clipboard component unit-tree app-state)}
        [:i.far.fa-copy.fa-lg.fa-fw]]
      [:span
       {:className class-paste
        :title "Paste"
        :onMouseDown #(swap! local-state-atom assoc :local-mode :canvas-paste)}
        [:i.far.fa-paste.fa-lg.fa-fw]]
      [:span.lft-sep]]))


(rum/defcs app-toolbar < (rum/local nil ::open)
  [toolbar-state component unit-tree app-state local-state-atom]
  (let [ open (::open toolbar-state)]
    [:div.map-local-menu
     {:onMouseDown jev/block-propagation
      :onTouchStart jev/block-propagation }
      (render-map-tools local-state-atom)
      (render-copy-tools component unit-tree app-state local-state-atom)
      (add-view-buttons component unit-tree)
      (add-button open nil :left
       {:id "poo"
        :title "Poo"
        :icon "far fa-poo"
        :label "Poo"
        :on-mouse-down #(js/console.log "Added poo") })
      (add-roll open nil :left
       {:id "file"
        :title "File"
        :icon "far fa-save"
        :label "File"
        :roll-items [
         {:id "save"
          :title "Save"
          :icon "far fa-download"
          :label "Save"
          :on-mouse-down #(js/console.log "Save")}
         {:id "load"
          :title "Load"
          :icon "far fa-upload"
          :label "Load"
          :on-mouse-down #(js/console.log "Load")}
         {:id "tohtml"
          :title "Export to HTML"
          :label "Export to HTML"
          :on-mouse-down #(js/console.log "Export to HTML")}
         ]})
      (add-roll open nil :left
       {:id "test"
        :title "Test"
        :icon "far fa-gift"
        :roll-items [
         {:id "save"
          :title "Save"
          :icon "far fa-download"
          :label "Save"
          :on-mouse-down #(js/console.log "Save")}
         {:id "load"
          :title "Load"
          :icon "far fa-upload"
          :label "Load"
          :on-mouse-down #(js/console.log "Load")}
         {:id "tohtml"
          :title "Export to HTML"
          :label "Export to HTML"
          :on-mouse-down #(js/console.log "Export to HTML")}
         ]})
      ]))
