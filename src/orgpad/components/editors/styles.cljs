(ns ^{:doc "Styles editor"}
  orgpad.components.editors.styles
  (:require [rum.core :as rum]
            [com.rpl.specter :as s :refer-macros [select transform setval]]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.cycle.life :as lc]
            [orgpad.tools.dscript :as ds]
            [orgpad.tools.orgpad :as ot]
            [orgpad.tools.orgpad-manipulation :as omt]
            [orgpad.tools.js-events :as jev]
            [orgpad.tools.rum :as trum]
            [orgpad.tools.dom :as dom]
            [orgpad.components.registry :as cregistry]
            [orgpad.components.input.slider :as slider]
            [orgpad.components.menu.color.picker :as cpicker]))

(def init-state
  {})

(def border-styles
  [ "none" "solid" "dotted" "dashed" "double" "groove" "ridge" "inset" "outset" ])

(defn frame
  [label & body]
  [:div.map-view-border-edit {}
   [:div.center label ]
   body])

(defn render-selection
  [styles style on-change]
  (into
   [:select.fake-center
    {:onMouseDown jev/stop-propagation
     :onMouseMove jev/stop-propagation
     :onBlur jev/stop-propagation
     :onChange #(on-change (-> % .-target .-value)) } ]
   (map (fn [s]
          [:option (if (= s style) { :selected true } {}) s])
        styles)))

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
                                                                 :max 100})))
          ]])

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
   (render-color-picker component style "Link Color" :orgpad/link-color)
   ])

(def style-type->editor
  {:orgpad.map-view/vertex-props-style render-vertex-props-style
   :orgpad.map-view/link-props-style render-link-props-style})

(defn render-style-editor
  [component active-type styles-list active-style]
  (let [style (->> styles-list (drop-while #(not= (:orgpad/style-name %) active-style)) first)]
    ((style-type->editor active-type) component style)))

(defn- gen-style-list-elem
  [component active-type active-style style]
  (let [name (:orgpad/style-name style)
        class-name (str "style-label " (if (= active-style name) "active" ""))]
    [:span.style {:key name} 
      [:span
       {:onClick #(lc/transact! component 
                     [[:orgpad/app-state [[:styles active-type :active-style] name]]])
        :className class-name}
        name]
      (when (not= name "default")
        [:span.style-button
         {:title "Remove style"
          :onClick #(lc/transact! component
                      [[:orgpad.style/remove 
                        {:id (:db/id style)
                         :name (:orgpad/style-name style)
                         :type (:orgpad/view-type style)}]
                        (when (= active-style name) 
                          [:orgpad/app-state [[:styles active-type :active-style] nil ]])
                        ])}
          [:i.far.fa-trash-alt]])]))

(defn- gen-style-list
  [component local-state active-type styles-list active-style]
  [:div.styles-list
    (map (partial gen-style-list-elem component active-type active-style) styles-list)
    [:span.new-style
      [:input {:type "text"
               :onChange #(swap! local-state assoc :new-style-name (-> % .-target .-value))
               :value (or (:new-style-name @local-state) "")
               :placeholder "new style name"}]
      [:span.far.fa-plus-circle
       {:onClick #(when (:new-style-name @local-state)
                   (lc/transact! component
                                [[:orgpad.style/new {:name (:new-style-name @local-state)
                                                     :type active-type}]
                                 [:orgpad/app-state [[:styles active-type :active-style]
                                                     (:new-style-name @local-state)]]])
                   (swap! local-state assoc :new-style-name nil)
                   )}]]])

(rum/defcc styles-editor < lc/parser-type-mixin-context (rum/local init-state)
  [component app-state on-close]
  (let [styles-types (styles-types-list)
        local-state (trum/comp->local-state component)
        active-type (or (-> app-state :styles :active-type) (-> styles-types first :key))
        styles-list (sort #(compare (:orgpad/style-name %1) (:orgpad/style-name %2))
                          (lc/query component :orgpad/styles {:view-type active-type} true))
        active-style (or (-> app-state :styles active-type :active-style)
                         (-> styles-list first :orgpad/style-name))]
    ;; (js/console.log styles-list)
    [:div.styles-editor
     [:div.header
      [:div.label "Styles"]
      [:div.close {:on-click on-close}
       [:span.far.fa-times-circle]]]
     [:div.styles-names
      (into [] (map (fn [s]
                      [:span {:onClick #(lc/transact! component [[:orgpad/app-state
                                                                  [[:styles :active-type] (:key s)]]])
                              :key (:key s)
                              :className (if (= active-type (:key s)) "active" "")}
                       (:name s)])
                    styles-types))]
     [:div.editor
      (gen-style-list component local-state active-type styles-list active-style) 
      [:div.style-editor
       (render-style-editor component active-type styles-list active-style)
       ]
      ]
     ]
    )
  )
