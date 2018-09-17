(ns ^{:doc "Styles editor"}
  orgpad.components.editors.styles
  (:require [rum.core :as rum]
            [com.rpl.specter :as s :refer-macros [select transform setval]]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.cycle.life :as lc]
            [orgpad.tools.colls :as colls]
            [orgpad.tools.dscript :as ds]
            [orgpad.tools.orgpad :as ot]
            [orgpad.tools.orgpad-manipulation :as omt]
            [orgpad.tools.styles :as styles]
            [orgpad.tools.js-events :as jev]
            [orgpad.tools.rum :as trum]
            [orgpad.tools.dom :as dom]
            [orgpad.components.registry :as cregistry]
            [orgpad.components.input.slider :as slider]
            [orgpad.components.graphics.primitives :as g]
            [orgpad.components.menu.color.picker :as cpicker]))

(def init-state
  {:style-box :hidden})

(def border-styles
  ["none" "solid" "dotted" "dashed" "double" "groove" "ridge" "inset" "outset"])

(defn frame
  [label & body]
  [:div.map-view-border-edit {}
   [:div.center label]
   body])

(defn- selection-option
  [selection name]
  (let [selected (= name selection)
        disabled (= name nil)]
    [:option {:key name :selected selected :disabled disabled} name]))

(defn render-selection
  [options selection on-change]
  (let [options' (if (= selection nil) (conj (seq options) nil) options)]
    (into [:select.fake-center
           {:onMouseDown jev/stop-propagation
            :onMouseMove jev/stop-propagation
            :onBlur jev/stop-propagation
            :onChange #(on-change (-> % .-target .-value))}]
          (map (partial selection-option selection) options'))))

(defn styles-types-list
  []
  (into #{}
        (comp
         (mapcat :orgpad/child-props-style-types))
        (vals (cregistry/get-registry))))

(defn transact!
  [component style name & [set-prop]]
  #(lc/transact! component [[:orgpad.style/update
                             {:style style
                              :prop-name name
                              :prop-val (if set-prop
                                          (set-prop style name %)
                                          %)}]]))

(defn slider-params
  [{:keys [component style prop-name min max get-prop set-prop]}]
  {:min min :max max
   :value (if get-prop
            (get-prop style prop-name)
            (prop-name style))
   :on-change (transact! component style prop-name set-prop)})

(defn render-sizes
  [component style]
  [:span [:div
          (frame "Width" (slider/render-slider (slider-params {:component component
                                                               :style style
                                                               :prop-name :orgpad/unit-width
                                                               :max js/window.innerWidth})))
          (frame "Height" (slider/render-slider (slider-params {:component component
                                                                :style style
                                                                :prop-name :orgpad/unit-height
                                                                :max js/window.innerHeight})))
          (frame "Border Width" (slider/render-slider (slider-params {:component component
                                                                      :style style
                                                                      :prop-name :orgpad/unit-border-width
                                                                      :max 100})))
          (frame "Padding" (slider/render-slider (slider-params {:component component
                                                                 :style style
                                                                 :prop-name :orgpad/unit-padding
                                                                 :max 100})))]])

(defn render-color-picker
  [component style label prop-name]
  [:span
   (frame label
          (cpicker/color-picker (prop-name style) {}
                                (transact! component style prop-name)))])

(defn render-corner-style
  [component style]
  [:span [:div
          (frame "Corner X" (slider/render-slider (slider-params {:component component
                                                                  :style style
                                                                  :prop-name :orgpad/unit-corner-x
                                                                  :max 100})))
          (frame "Corner Y" (slider/render-slider (slider-params {:component component
                                                                  :style style
                                                                  :prop-name :orgpad/unit-corner-y
                                                                  :max 100})))
          (frame "Border Style" (render-selection border-styles
                                                  (:orgpad/unit-border-style style)
                                                  (transact! component style :orgpad/unit-border-style)))]])

(defn render-vertex-props-style
  [component style]
  [(render-sizes component style)
   (render-corner-style component style)
   (render-color-picker component style "Border Color" :orgpad/unit-border-color)
   (render-color-picker component style "Background Color" :orgpad/unit-bg-color)])

(defn get-nth-component
  [n style prop-name]
  (get-in style [prop-name n]))

(defn set-nth-component
  [n style prop-name val]
  (-> style
      prop-name
      (assoc n val)))

(defn set-nth-component-js
  [n style prop-name val]
  (let [a (-> style prop-name aclone)]
    (aset a n val) a))

(defn render-link-types
  [component style]
  (let [undirected-style (str "btn" (when (= (:orgpad/link-type style) :undirected) " active"))
        directed-style (str "btn" (when (= (:orgpad/link-type style) :directed) " active"))
        bidirected-style (str "btn" (when (= (:orgpad/link-type style) :bidirected) " active"))]
    [:div.btn-panel
     [:span.fill]
     [:span
      {:class undirected-style
       :title "Undirected link"
       :on-click #((transact! component style :orgpad/link-type) :undirected)}
      [:i.far.fa-minus]]
     [:span
      {:class directed-style
       :title "Directed link"
       :on-click #((transact! component style :orgpad/link-type) :directed)}
      [:i.far.fa-long-arrow-right]]
     [:span
      {:class bidirected-style
       :title "Directed link both ways"
       :on-click #((transact! component style :orgpad/link-type) :bidirected)}
      [:i.far.fa-arrows-h]]
     [:span.fill]]))

(defn render-link-sizes
  [component style]
  ;; (js/console.log "render-link-sizes" style)
  [:span [:div
          (frame "Border Width" (slider/render-slider (slider-params {:component component
                                                                      :style style
                                                                      :prop-name :orgpad/link-width
                                                                      :max 100})))
          (frame "Dash style"
                 (slider/render-slider (slider-params {:component component
                                                       :style style
                                                       :prop-name :orgpad/link-dash
                                                       :max 100
                                                       :get-prop (partial get-nth-component 0)
                                                       :set-prop (partial set-nth-component-js 0)}))
                 (slider/render-slider (slider-params {:component component
                                                       :style style
                                                       :prop-name :orgpad/link-dash
                                                       :max 100
                                                       :get-prop (partial get-nth-component 1)
                                                       :set-prop (partial set-nth-component-js 1)})))
          ;(frame "Mid point relative position"
          ;       (slider/render-slider (slider-params {:component component
          ;                                             :style style
          ;                                             :prop-name :orgpad/link-mid-pt
          ;                                             :max 1000
          ;                                             :get-prop (partial get-nth-component 0)
          ;                                             :set-prop (partial set-nth-component 0)}))
          ;       (slider/render-slider (slider-params {:component component
          ;                                             :style style
          ;                                             :prop-name :orgpad/link-mid-pt
          ;                                             :max 1000
          ;                                             :get-prop (partial get-nth-component 1)
          ;                                             :set-prop (partial set-nth-component 1)})))
          (frame "Arrow position" (slider/render-slider (slider-params {:component component
                                                                        :style style
                                                                        :prop-name :orgpad/link-arrow-pos
                                                                        :min 1 :max 100})))
          (frame "Direction" (render-link-types component style))]])

(defn render-link-props-style
  [component style]
  [(render-link-sizes component style)
   (render-color-picker component style "Link Color" :orgpad/link-color)])

(def style-type->editor
  {:orgpad.map-view/vertex-props-style render-vertex-props-style
   :orgpad.map-view/link-props-style render-link-props-style})

(defn- example-vertex-props-style
  [style]
  [:div.style-example
   [:div.unit-example {:style (styles/prop->css style)}
    [:center [:h3 "Lorem Ipsum"]]
    [:p "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec non nisl euismod, auctor libero quis, scelerisque lorem. Cras quis iaculis sem. Phasellus congue dignissim libero, at auctor dui sodales a."]]])

(defn- example-link-props-style
  [style]
  (let [link-type (style :orgpad/link-type)
        start-pos [25 50]
        end-pos [225 50]
        ctl-pt [125 25]
        width (+ 250 (style :orgpad/link-width))
        height (+ 75 (style :orgpad/link-width))]
    [:div.style-example
     [:div {:style {:width width :height height}}
      (g/quadratic-curve start-pos end-pos ctl-pt {:canvas (styles/gen-link-canvas style)})
      (when (not= link-type :undirected)
        (g/make-arrow-quad start-pos end-pos ctl-pt style))
      (when (= link-type :bidirected)
        (g/make-arrow-quad end-pos start-pos ctl-pt style))]]))

(def style-type->example
  {:orgpad.map-view/vertex-props-style example-vertex-props-style
   :orgpad.map-view/link-props-style example-link-props-style})

(defn- render-style-editor
  [component active-type styles-list active-style]
  (let [style (->> styles-list (drop-while #(not= (:orgpad/style-name %) active-style)) first)]
    [:div.style-editor
     [:div.style-props
      ((style-type->editor active-type) component style)]
     ((style-type->example active-type) style)]))

(defn- set-edit-style-box
  [local-state style]
  (swap! local-state assoc
         :style-box :edit
         :style-name (:orgpad/style-name style)
         :edited-style style
         :style-based-on nil))

(defn- remove-style
  [component local-state style active-type active-style]
  (let [name (:orgpad/style-name style)
        remove-tr [:orgpad.style/remove {:id (:db/id style)
                                         :name name
                                         :type (:orgpad/view-type style)}]
        selection-tr [:orgpad/app-state [[:styles active-type :active-style] nil]]]
    (lc/transact! component
                  (if (= active-style name)
                    [remove-tr selection-tr]
                    [remove-tr]))
    (swap! local-state assoc :style-box :hidden)))

(defn- change-active-style
  [component local-state active-type new-style]
  (lc/transact! component
                [[:orgpad/app-state [[:styles active-type :active-style] new-style]]])
  (swap! local-state assoc :style-box :hidden))

(defn- gen-style-list-elem
  [component local-state active-type active-style style]
  (let [name (:orgpad/style-name style)
        class-name (str "style-label " (if (= active-style name) "active" ""))]
    [:span.style {:key name}
     [:span
      {:onClick #(change-active-style component local-state active-type name)
       :className class-name}
      name]
     (when (not= name "default")
       (list
        [:span.style-btn
         {:key "edit-btn"
          :title "Edit style"
          :onClick #(set-edit-style-box local-state style)}
         [:i.far.fa-pencil]]
        [:span.style-btn
         {:title "Remove style"
          :key "remove-btn"
          :onClick #(remove-style component local-state style active-type active-style)}
         [:i.far.fa-trash-alt]]))]))

(defn- set-new-style-box
  [local-state]
  (swap! local-state assoc :style-box :new)
  (swap! local-state assoc :style-name nil)
  (swap! local-state assoc :style-based-on "default"))

(defn- add-new-style
  [component local-state active-type]
  (lc/transact! component
                [[:orgpad.style/new {:name (:style-name @local-state)
                                     :type active-type
                                     :based-on (:style-based-on @local-state)}]
                 [:orgpad/app-state [[:styles active-type :active-style]
                                     (:style-name @local-state)]]])
  (swap! local-state assoc :style-box :hidden))

(defn- edit-style
  [component local-state active-type]
  (let [style (:edited-style @local-state)
        id (:db/id style)
        old-name (:orgpad/style-name style)
        new-name (:style-name @local-state)
        based-on (:style-based-on @local-state)
        rebase-query (when based-on
                       [[:orgpad.style/rebase {:id id
                                               :name old-name
                                               :type active-type
                                               :based-on based-on}]])
        rename-query (when (not= old-name new-name)
                       [[:orgpad.style/rename {:id id
                                               :old-name old-name
                                               :new-name new-name
                                               :type active-type}]
                        [:orgpad/app-state [[:styles active-type :active-style]
                                            (:style-name @local-state)]]])]
    (js/console.log (colls/minto [] rebase-query rename-query))
    (when (or rebase-query rename-query)
      (lc/transact! component
                    (colls/minto [] rebase-query rename-query)))
    (swap! local-state assoc :style-box :hidden)))

(defn- gen-new-edit-style
  [component local-state active-type mode styles-list]
  (let [style-name (:style-name @local-state)
        enabled (and (not= style-name nil) (not= style-name ""))
        based-on-label (if (= mode :new) "Based on:" "Rewrite with:")
        apply-btn-icon (if (= mode :new) "fa-plus-circle" "fa-pencil")
        apply-btn-label (if (= mode :new) "Create" "Change")
        apply-btn-class (str "btn" (when (not enabled) " disabled"))]
    (list
     [:span.edit
      {:key "style-name"}
      [:span.label "Style name:"]
      [:input {:type "text"
               :onChange #(swap! local-state assoc :style-name (-> % .-target .-value))
               :value (or style-name "")}]]
     [:span.edit
      {:key "based-on"}
      [:span.label based-on-label]
      (render-selection (map :orgpad/style-name styles-list) (:style-based-on @local-state)
                        #(swap! local-state assoc :style-based-on %))]
     [:span.btn-box
      [:span
       {:className apply-btn-class
        :onClick #(when enabled
                    (if (= mode :new)
                      (add-new-style component local-state active-type)
                      (edit-style component local-state active-type)))}
       [:i {:className (str "far " apply-btn-icon)}]
       [:span.btn-icon-label apply-btn-label]]
      [:span.btn
       {:onClick #(swap! local-state assoc :style-box :hidden)}
       [:span.btn-label "Cancel"]]])))

(defn- gen-style-box
  [component local-state active-type styles-list]
  (let [mode (:style-box @local-state)
        gen-new-edit-style' (partial gen-new-edit-style component local-state active-type mode)]
    [:span.style-box
     (case mode
       :hidden [:span.btn-box
                [:span.btn
                 {:onClick #(set-new-style-box local-state)}
                 [:i.far.fa-plus-circle]
                 [:span.btn-icon-label "Create a new style"]]]
       :new (gen-new-edit-style' styles-list)
       :edit (gen-new-edit-style'
              (remove #(= % (:edited-style @local-state)) styles-list)))]))

(defn- gen-left-panel
  [component local-state active-type styles-list active-style]
  [:div.left-panel
   [:div.styles-list
    (map (partial gen-style-list-elem component local-state active-type active-style) styles-list)]
   (gen-style-box component local-state active-type styles-list)])

(defn- change-active-type
  [component local-state new-type]
  (lc/transact! component [[:orgpad/app-state [[:styles :active-type] new-type]]])
  (swap! local-state assoc :style-box :hidden))

(rum/defcc styles-editor < lc/parser-type-mixin-context (rum/local init-state)
  [component app-state on-close]
  (let [styles-types (styles-types-list)
        local-state (trum/comp->local-state component)
        active-type (or (-> app-state :styles :active-type) (-> styles-types first :key))
        styles-list (styles/get-sorted-style-list component active-type)
        active-style (or (-> app-state :styles active-type :active-style)
                         (-> styles-list first :orgpad/style-name))]
    ;; (js/console.log styles-list)
    [:div.styles-editor
     [:div.header
      [:div.label "Styles"]
      [:div.close {:on-click on-close}
       [:span.far.fa-times-circle]]]
     [:div.styles-names
      (map (fn [s] [:span {:onClick #(change-active-type component local-state (:key s))
                           :key (:key s)
                           :className (if (= active-type (:key s)) "active" "")}
                    (:name s)])
           styles-types)]
     [:div.editor
      (gen-left-panel component local-state active-type styles-list active-style)
      (render-style-editor component active-type styles-list active-style)]]))
