(ns ^{:doc "Map unit component"}
  orgpad.components.map.unit-editor
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.cycle.life :as lc]
            [orgpad.components.queries :as qs]
            [orgpad.components.registry :as registry]
            [orgpad.components.menu.circle :as mc]
            [orgpad.components.node :as node]
            [orgpad.tools.css :as css]
            [orgpad.tools.js-events :as jev]
            [orgpad.tools.rum :as trum]))

(def ^:private padding 20)
(def ^:private diam (- (* padding 2) 5))

(def ^:private menu-conf {
  :always-open? true
  :init-state true
  :init-rotate -135
  :init-scale 0.5
  :child-init-scale 0.2
  :child-init-rotation -180
  :main-spring-config #js [500 30]
  :child-diam diam
  :main-diam diam
  :offset 0.4
  :child-class "circle-menu-child"
  :main-class "circle-menu-child"
  :final-child-pos-fn mc/final-child-delta-fix-pos
  :child-spring-config #js [800 50]
})

(defn- compute-children-position
  [prop]
  (let [w (prop :orgpad/unit-width)
        h (prop :orgpad/unit-height)]
    [{ :dx (- (/ w 2))
       :dy padding }
     { :dx (- (+ w padding))
       :dy padding }
     { :dx (- (+ w padding))
       :dy (- (+ h padding)) }
     { :dx padding
       :dy (- (+ h padding)) }
     ]))

(defn- selected-unit-prop
  [{:keys [unit] :as unit-tree} unit-id prop-id]
  (let [sel-unit
        (->> unit
             :orgpad/refs
             (filter (fn [{:keys [unit]}] (= (unit :db/id) unit-id)))
             first)
        sel-prop
        (->> sel-unit
             :props
             (filter (fn [prop] (= (prop :db/id) prop-id)))
             first)]
    [sel-unit sel-prop]))

(defn- open-unit
  [component { :keys [unit view path-info] }]
  (let [{ :keys [orgpad/view-name orgpad/view-type] } view
        view-path (path-info :orgpad/view-path)]
    (lc/transact! component [[ :orgpad/root-view-stack { :db/id (unit :db/id)
                                                         :orgpad/view-name view-name
                                                         :orgpad/view-type view-type
                                                         :orgpad/view-path view-path } ]])))

(rum/defcc unit-editor < lc/parser-type-mixin-context
  [component {:keys [view] :as unit-tree} app-state local-state]
  (let [select-unit (@local-state :selected-unit)]
    (when select-unit
      (let [[old-unit old-prop] select-unit
            [unit prop] (selected-unit-prop unit-tree (-> old-unit :unit :db/id) (old-prop :db/id))
            pos (prop :orgpad/unit-position)
            style (merge { :width (prop :orgpad/unit-width)
                           :height (prop :orgpad/unit-height) }
                         (css/transform { :translate pos }))]
        (when (or (not= old-unit unit) (not= old-prop prop))
          (swap! local-state merge { :selected-unit [unit prop view] }))
        [:div {}
         [ :div { :className "map-view-unit-selected" :style style :key 0 } ]
         (mc/circle-menu
          (merge menu-conf { :center-x (- (pos 0) padding)
                             :center-y (- (pos 1) padding)
                             :children-positions (compute-children-position prop)
                             :onMouseDown jev/block-propagation
                             :onMouseUp jev/block-propagation })
          [ :i { :title "Move"
                 :className "fa fa-arrows fa-lg"
                 :onMouseDown #(swap! local-state merge { :local-mode :unit-move
                                                          :mouse-x (.-clientX %)
                                                          :mouse-y (.-clientY %) })
                 :onMouseUp #(swap! local-state merge { :local-mode :none }) } ]
          [ :i { :title "Edit"
                 :className "fa fa-pencil-square-o fa-lg"
                 :onMouseUp #(open-unit component unit)
                 } ]
          [ :i { :title "Properties" :className "fa fa-cogs fa-lg" } ]
          [ :i { :title "Resize"
                 :className "fa fa-arrows-alt fa-lg"
                 :onMouseDown #(swap! local-state merge { :local-mode :unit-resize
                                                          :mouse-x (.-clientX %)
                                                          :mouse-y (.-clientY %) })
                 :onMouseUp #(swap! local-state merge { :local-mode :none })} ]
          [ :i { :title "Link" :className "fa fa-link fa-lg" } ]
          )
         ]
      ))))
