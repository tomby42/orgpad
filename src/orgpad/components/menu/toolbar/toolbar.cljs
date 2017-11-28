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
            [orgpad.components.input.file :as if]
            [orgpad.tools.dom :as dom]
            [goog.string :as gstring]
            [goog.string.format]))

;; input format for toolbar data
;; =============================
;;
;; two lists, one for left-aligned buttons, one for right-aligned buttons
;;
;; each list contains one element for each group of buttons
;;
;; each group of buttons is represented by a nested list
;;
;; each button is represented by the following map
;;  {:elem (:btn|:roll|:text) 
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
;;  {:elem = :load   ... may be omitted, when load, special hack for file loading is used
;;   :id              ...   identificator
;;   :title           ...   tooltip hint
;;   :icon            ...   font-awesome style name or nil for no icon
;;   :label           ...   displayed label or nil for no label
;;   :on-mouse-down   ...   function on mouse down
;;   :active          ...   function returning true/false whether button should be active, possibly nil
;;  }
;;
;; all used functions are unary, having access to the map param given on the input

(defn- toggle-open-state
  "Toggle which roll is open. Opens clicked-roll unless it is already opened, in which case it is closed."
  [open clicked-roll]
  (if (= open clicked-roll) nil clicked-roll))

(defn- wrap-toolbar-action
  "Close any open roll and proceed with function f."
  [local-state f]
  (swap! local-state assoc-in [:open] nil)
  (f))

(defn- gen-button
  "Generates one button from the input data."
  [local-state params {:keys [id title icon label on-mouse-down active]}]
  (let [is-active (when active (active params))
        button-class (str "btn" (when is-active " active"))
        label-class (if icon "btn-icon-label" "btn-label")]
    [:span
      {:key id
       :className button-class
       :title title
       :onMouseDown #(wrap-toolbar-action local-state (fn [] (on-mouse-down params %))) }
       (when icon [:i { :className (str icon " fa-lg fa-fw") }])
       (when label [:span { :className label-class } label])]))
 
(defn- gen-roll-item
  "Generates one roll item from the input data, with a hack for file loading."
  [local-state params {:keys [elem id title icon label on-mouse-down active]}]
  (let [is-active (when active (active params))
        label-class (if icon "roll-icon-label" "roll-label")
        icon-span (when icon [:i { :className (str icon " fa-lg fa-fw") }])
        label-span (when label [:span { :className label-class } label])]
    (if (= elem :load)
      (if/file-input { :on-change #(wrap-toolbar-action local-state (fn [] (on-mouse-down params %)))
                       :attr {:className "roll-item" :key id :title title} }
        icon-span label-span)
      [:span.roll-item
        {:key id
         :title title
         :onMouseDown #(wrap-toolbar-action local-state (fn [] (on-mouse-down params %))) }
         icon-span label-span])))

(defn- gen-roll
  "Generates one roll from the input data."
  [local-state params {:keys [id title icon label active roll-items] :as data}]
  (let [is-active (when active (active params))
        button-class (str "btn" (when (or is-active (= (:open @local-state) id)) " active"))
        label-class (if icon "btn-icon-label" "btn-label")]
    [:span.roll {:key id}
      [:span 
       {:className button-class
        :title title
        :onMouseDown (jev/make-block-propagation #(swap! local-state update-in [:open] toggle-open-state id))}
        (when icon [:i { :className (str icon " fa-lg fa-fw") }])
        (when label [:span { :className label-class } label])
        [:i { :className "fa fa-caret-down" }]]
      (when (= (:open @local-state) id)
        [:span.roll-items
          (map (partial gen-roll-item local-state params) roll-items)])]))

(defn- gen-text
  "Generates one text from the input data."
  [local-state params {:keys [label]}]
  [:span.text label])

(defn- gen-element
  "Generates one element of arbitrary type from the input data."
  [local-state params {:keys [elem] :as data}]
  (case elem
    :btn (gen-button local-state params data)
    :roll (gen-roll local-state params data)
    :text (gen-text local-state params data)))

(defn- gen-section
  "Generates one section of the toolbar (between two separators) from the input data."
  [local-state params data]
  (map (partial gen-element local-state params) data))

(defn- gen-side
  "Generates one side of the toolbar from the input data."
  [local-state params data]
  (interpose [:span.sep]
    (map (partial gen-section local-state params) data)))

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
  [:span.uedit-toolbar
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


(rum/defcc app-toolbar < (rum/local {:open nil}) lc/parser-type-mixin-context
  [component params left-data right-data]
  (let [local-state (trum/comp->local-state component)
        extended-params (assoc params :component component)]
    (js/console.log (str "component: " (pr component)))
    (js/console.log (str "local-state: " (pr local-state)))
    [:div.toolbar
     {:onMouseDown jev/block-propagation
      :onTouchStart jev/block-propagation }
      (gen-side local-state extended-params left-data)
      [:span.fill]
      (gen-side local-state extended-params right-data)

      ;(render-map-tools local-state-atom)
      ;(render-copy-tools component unit-tree app-state local-state-atom)
      ;(add-view-buttons component unit-tree)
    ]))
