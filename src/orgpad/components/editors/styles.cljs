(ns ^{:doc "Styles editor"}
  orgpad.components.editors.styles
  (:require [rum.core :as rum]
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

(def ^:private border-styles
  [ "none" "solid" "dotted" "dashed" "double" "groove" "ridge" "inset" "outset" ])

(defn frame
  [label & body]
  [:div.map-view-border-edit {}
   [:div.center label ]
   body])

(defn border-style
  [style on-change]
  (into
   [:select.fake-center
    {:onMouseDown jev/stop-propagation
     :onMouseMove jev/stop-propagation
     :onBlur jev/stop-propagation
     :onChange on-change } ]
   (map (fn [s]
          [:option (if (= s style) { :selected true } {}) s])
        border-styles)))

(defn styles-types-list
  []
  (into #{}
        (comp
         (mapcat :orgpad/child-props-style-types))
        (vals (cregistry/get-registry))))

(defn transact!
  [component style name]
  #(lc/transact! component [[:orgpad.style/update
                             {:style style
                              :prop-name name
                              :prop-val %}]]))

(defn slider-params
  [component style prop-name max]
  {:max max
   :value (prop-name style)
   :on-change (transact! component style prop-name)})

(defn render-sizes
  [component style]
  [:span [:div
          (frame "Width" (slider/render-slider (slider-params component style :orgpad/unit-width
                                                              js/window.innerWidth)))
          (frame "Height" (slider/render-slider (slider-params component style :orgpad/unit-height
                                                               js/window.innerHeight)))
          (frame "Border Width" (slider/render-slider (slider-params component style :orgpad/unit-border-width
                                                                     100)))
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
          (frame "Corner X" (slider/render-slider (slider-params component style :orgpad/unit-corner-x 100)))
          (frame "Corner Y" (slider/render-slider (slider-params component style :orgpad/unit-corner-y 100)))
          (frame "Border Style" (border-style (:orgpad/unit-border-style style)
                                              (transact! component style :orgpad/unit-border-style)))]])

(defn render-vertex-props-style
  [component style]
  [(render-sizes component style)
   (render-corner-style component style)
   (render-color-picker component style "Border Color" :orgpad/unit-border-color)
   (render-color-picker component style "Background Color" :orgpad/unit-bg-color)
   ]
  )

(defn render-link-props-style
  [component style]
  )

(def style-type->editor
  {:orgpad.map-view/vertex-props-style render-vertex-props-style
   :orgpad.map-view/link-props-style render-link-props-style})

(defn render-style-editor
  [component active-type styles-list active-style]
  (let [style (->> styles-list (drop-while #(not= (:orgpad/style-name %) active-style)) first)]
    ((style-type->editor active-type) component style)))

(rum/defcc styles-editor < lc/parser-type-mixin-context (rum/local init-state)
  [component db app-state on-close]
  (let [styles-types (styles-types-list)
        local-state (trum/comp->local-state component)
        active-type (or (:active-type @local-state) (-> styles-types first :key))
        styles-list (lc/query component :orgpad/styles {:view-type active-type} true)
        active-style (or (:active-style @local-state) (-> styles-list first :orgpad/style-name))]
    (js/console.log styles-list)
    [:div.styles-editor
     [:div.header
      [:div.label "Styles"]
      [:div.close {:on-click on-close}
       [:span.far.fa-times-circle]]]
     [:div.styles-names
      (into [] (map (fn [s]
                      [:span {:onClick #()
                              :key (:key s)
                              :className (if (= active-type (:key s)) "active" "")}
                       (:name s)])
                    styles-types))]
     [:div.editor
      [:div.styles-list
       (into [] (map (fn [s]
                       [:span {:onClick #()
                               :key (:orgpad/style-name s)
                               :className (if (= active-style (:orgpad/style-name s)) "active" "")}
                        (:orgpad/style-name s)])
                     styles-list))
       [:span "New" ]
       ]
      [:div.style-editor
       (render-style-editor component active-type styles-list active-style)
       ]
      ]
     ]
    )
  )
