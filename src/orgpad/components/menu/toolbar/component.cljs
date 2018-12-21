(ns ^{:doc "Toolbar component"}
  orgpad.components.menu.toolbar.component
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

;; Input format for toolbar data
;; =============================
;;
;; Two lists, one for left-aligned buttons, one for right-aligned buttons.
;;
;; Each list contains one element for each group of buttons.
;;
;; Each group of buttons is represented by a nested list.
;;
;; Each button is represented by the following map:
;;  {:elem (:btn|:roll|:text|:custom)
;;   :id              ...   identificator
;;   :title           ...   tooltip hint
;;   :icon            ...   font-awesome style name or nil for no icon
;;   :label           ...   displayed label or nil for no label
;;   :on-click        ...   binary function on click
;;   :active          ...   true/false or a unary function returning true/false whether button should be active
;;   :disabled        ...   true/false or a unary function returning true/false whether button should be disabled
;;        button is only active when it is not disabled
;;   :hidden          ...   true/false or a unary function returning true/false whether button should be hidden
;;        hidden buttons are completely ignored in data, when all buttons in some section are
;;        hidden, the section is ignored
;;   :load-files      ...   may be omitted, true/false, special hack for file loading is used
;;
;;   for :roll only
;;   :roll-items      ...   list of all roll items
;;
;;   for :text only
;;   :value           ...   unary function generating a string to be displayed
;;  }
;;
;; Each roll item is represented by the same map without elem (at least for now).
;; In the future, it should be possible to do a menu with multiple layers in this way.
;;
;; The first parameter of all functions is the map param given on the input, the second parameter
;; (if it exists) is the Javascript event.

(defn- get-hidden
  "Button is hidden if hidden is true or hidden function returns true."
  [hidden params]
  (if (fn? hidden) (hidden params) hidden))

(defn- visible-elem?
  "Return true/false whether element is visible."
  [params {:keys [hidden]}]
  (not (get-hidden hidden params)))

(defn- filter-roll-items
  "Returns data for roll with all hidden roll-items filtered out."
  [params {:keys [roll-items] :as data}]
  (assoc data :roll-items (filter (partial visible-elem? params) roll-items)))

(defn- filter-section
  "Returns data for section with all hidden elements filtered out."
  [params data]
  (filter (partial visible-elem? params)
          (map #(if (= (:elem %) :roll) (filter-roll-items params %) %) data)))

(defn- filter-side
  "Returns data for side with hidden elements in all sections filtered out and removed empty sections."
  [params data]
  (filter #(> (count %) 0) (map #(filter-section params %) data)))

(defn- toggle-open-state
  "Toggle which roll is open. Opens clicked-roll unless it is already opened, in which case it is closed."
  [open clicked-roll]
  (if (= open clicked-roll) nil clicked-roll))

(defn- wrap-toolbar-action
  "Close any open roll and proceed with function f."
  [local-state f]
  (swap! local-state assoc-in [:open] nil)
  (f))

(defn- close-toolbar
  "Close any open roll."
  [local-state]
  (swap! local-state assoc-in [:open] nil))

(defn- gen-action
  "Generate function for js-event."
  [is-disabled func local-state params]
  (when (and (fn? func) (not is-disabled))
    #(wrap-toolbar-action local-state (fn [] (func params %)))))

(defn- get-disabled
  "Button is disabled if disabled is true or disabled function returns true."
  [disabled params]
  (if (fn? disabled) (disabled params) disabled))

(defn- get-active
  "Button is active if it is not disabled and active is true or active function returns true."
  [active is-disabled params]
  (when (not is-disabled) (if (fn? active) (active params) active)))

(defn- gen-button
  "Generates one button or roll item from the input data, with a hack for file loading."
  [local-state params elem {:keys [id title icon label on-click on-mouse-down on-touch-start active disabled load-files]}]
  (let [is-disabled (get-disabled disabled params)
        is-active (get-active active is-disabled params)
        button-class (str elem (when is-disabled " disabled") (when is-active " active"))
        label-class (if icon "btn-icon-label" "btn-label")
        icon-span (when icon [:i {:key (str id "-icon") :className (str icon " fa-lg fa-fw")}])
        label-span (when label [:span {:key (str id "-label") :className label-class} label])]
    (if (and load-files (not is-disabled))
      (if/file-input {:on-change #(wrap-toolbar-action local-state (fn [] (on-click params %)))
                      :attr {:className button-class :key id :title title}}
                     icon-span label-span)
      [:span
       {:key id
        :className button-class
        :title title
        :onClick (gen-action is-disabled on-click local-state params)
        :onMouseDown (gen-action is-disabled on-mouse-down local-state params)
        :onTouchStart (gen-action is-disabled on-touch-start local-state params)}
       icon-span label-span])))

(defn- gen-roll
  "Generates one roll from the input data."
  [local-state params {:keys [id title icon label active disabled roll-items] :as elem}]
  (when (visible-elem? params elem)
    (let [is-disabled (get-disabled disabled params)
          is-active (get-active active is-disabled params)
          button-class (str "btn" (when is-disabled " disabled") (when (or is-active (= (:open @local-state) id)) " active"))
          label-class (if icon "btn-icon-label" "btn-label")]
      [:span.roll {:key id}
       [:span
        {:key (str id "-btn")
         :className button-class
         :title title
         :onClick (when (not is-disabled) (jev/make-block-propagation #(swap! local-state update-in [:open] toggle-open-state id)))}
        (when icon [:i {:key (str id "-icon") :className (str icon " fa-lg fa-fw")}])
        (when label [:span {:key (str id "-label") :className label-class} label])
        [:i {:key (str id "-caret") :className "fa fa-caret-down"}]]
       (when (= (:open @local-state) id)
         [:span.roll-items {:key (str id "-roll-items")}
          (map (partial gen-button local-state params "roll-item") roll-items)])])))

(defn- gen-text
  "Generates one text from the input data."
  [local-state params {:keys [id value] :as data}]
  [:span.text {:key id} (when value (value params))])

(defn- gen-custom
  "Generate custom component by given render function"
  [local-state params {:keys [id render style] :as data}]
  [:span {:key id :className style} (when render (render params data local-state))])

(defn- gen-element
  "Generates one element of arbitrary type from the input data."
  [local-state params {:keys [elem] :as data}]
  (case elem
    :btn (gen-button local-state params "btn" data)
    :roll (gen-roll local-state params data)
    :text (gen-text local-state params data)
    :custom (gen-custom local-state params data)
    (js/console.warn "Toolbar gen-element: No matching element to " (pr-str elem))))

(defn- gen-section
  "Generates one section of the toolbar (between two separators) from the input data."
  [local-state params data]
  (map (partial gen-element local-state params) data))

(defn- gen-side
  "Generates one side of the toolbar from the input data, interposing each section with separators."
  [local-state params data]
  (let [sep-data (map #(identity [:span.sep {:key (str (:id (nth % 0)) "-sep")}]) data)]
    (drop-last (interleave
                (map (partial gen-section local-state params) data)
                sep-data))))

(rum/defcc toolbar < (rum/local {:open nil}) lc/parser-type-mixin-context
  "Toolbar component"
  [component toolbar-class params left-data right-data]
  (let [local-state (trum/comp->local-state component)
        params (assoc params :component component)]
    [:div (merge-with merge
                      {:className toolbar-class
                       :onMouseDown jev/block-propagation
                       :onTouchStart jev/block-propagation
                       :onDoubleClick jev/block-propagation}
                      (when (:left params)
                        {:style {:left (:left params)}})
                      (when (:style params)
                        {:style (:style params)}))
     (gen-side local-state params (filter-side params left-data))
     [:span.fill]
     (gen-side local-state params (filter-side params right-data))]))

(defn- view-types-roll-items
  "Get a list of available views except :orgpad/root-view as roll items."
  [current-type unit-tree-key]
  (->> (dissoc (registry/get-registry) :orgpad/root-view)
       (map (fn [[view-type info]] {:id view-type
                                    :label (info :orgpad/view-name)
                                    :icon (info :orgpad/view-icon)
                                    :on-click #(omt/change-view-type (:component %1) (unit-tree-key %1) view-type)
                                    :active (= current-type view-type)}))
       (sort-by :label)))

(defn gen-view-types-roll
  "Generate roll of available views, for view, where unit-tree-key is the key for unit-tree in params."
  [view unit-tree-key title-prefix id hidden]
  (let [current-info (-> view :orgpad/view-type registry/get-component-info)
        current-name (:orgpad/view-name current-info)
        current-icon (:orgpad/view-icon current-info)]
    {:elem :roll
     :id id
     :icon current-icon
     :title (str title-prefix ": " current-name)
     :roll-items (view-types-roll-items (:orgpad/view-type view) unit-tree-key)
     :hidden hidden}))
