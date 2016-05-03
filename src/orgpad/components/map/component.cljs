(ns ^{:doc "Map component"}
  orgpad.components.map.component
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.cycle.life :as lc]
            [orgpad.components.queries :as qs]
            [orgpad.components.registry :as registry]
            [orgpad.components.menu.circle :as mc]
            [orgpad.tools.css :as css]
            [orgpad.tools.rum :as trum]))

(def ^:private init-state
  { :show-local-menu false
    :local-mode :none
    :mouse-x 0
    :mouse-y 0 })

(def ^:private menu-conf {
  :always-open? true
  :init-state true
  :init-rotate -135
  :init-scale 0.5
  :main-spring-config #js [500 30]
  :fly-out-radius 80
  :base-angle 45
  :separation-angle 40
  :child-diam 40
  :child-init-scale 0.2
  :child-init-rotation -180
  :main-diam 60
  :offset 0.4
  :child-class "circle-menu-child"
  :final-child-pos-fn mc/final-child-delta-pos-rot
  :child-spring-config #js [400 28]
})

(defn render-children-units
  [childs view]
  (html
   [ :div { :className "map-view-canvas" :key 0 } ]))

(defn- block-propagation
  [ev]
  (.stopPropagation ev))

(defn- hide-local-menu
  [component]
  (let [local-state (trum/comp->local-state component)]
    (swap! local-state merge { :show-local-menu false })))

(defn- show-local-menu
  [component]
  (let [local-state (trum/comp->local-state component)]
    (swap! local-state merge { :show-local-menu false })))

(defn- create-pair-unit
  [component unit-tree pos ev]
  (lc/transact! component
                [[ :orgpad.units/new-pair-unit
                   { :parent (-> unit-tree :unit :db/id)
                     :position pos } ]] ))

(defn render-local-menu
  [component unit-tree app-state local-state]
  (when (local-state :show-local-menu)
    (let [pos { :center-x (local-state :mouse-x)
                :center-y (local-state :mouse-y) }]
      (mc/circle-menu (merge menu-conf pos { :onMouseDown block-propagation
                                             :onMouseUp block-propagation })
       [ :i { :className "fa fa-file-text-o fa-2x"
              :onMouseUp #(do
                            (create-pair-unit component unit-tree pos %)
                            (hide-local-menu component)) } ]
       [ :i { :className "fa fa-arrows fa-lg" } ]
       [ :i { :className "fa fa-plus fa-lg" } ]
       [ :i { :className "fa fa-minus fa-lg" } ] )) ))

(defn handle-mouse-down
  [component unit-tree app-state ev]
  (let [local-state (trum/comp->local-state component)]
    (swap! local-state merge { :mouse-x (.-clientX ev)
                               :mouse-y (.-clientY ev)
                               :local-mode :mouse-down
                               :show-local-menu false })
    ))

(defn handle-mouse-up
  [component unit-tree app-state ev]
  (let [local-state (trum/comp->local-state component)]

    (when (= (:local-mode @local-state) :mouse-down)
      (swap! local-state merge { :show-local-menu true }))
    ))

(defn handle-mouse-move
  [component unit-tree app-state ev]
  )

(defn handle-blur
  [component unit-tree app-state ev]
  )

(defn render-write-mode
  [component unit-tree app-state]
  (let [{:keys [unit view]} unit-tree
        local-state (trum/comp->local-state component)]
    (html
     [ :div { :className "map-view"
              :onMouseDown #(handle-mouse-down component unit-tree app-state %)
              :onMouseUp #(handle-mouse-up component unit-tree app-state %)
              :onMouseMove #(handle-mouse-move component unit-tree app-state %)
              :onBlur #(handle-blur component unit-tree app-state %) }
       (render-children-units (:orgpad/refs unit) view)
       (render-local-menu component unit-tree app-state @local-state)
      ])))

(defn render-read-mode
  [component unit-tree app-state]
  )

(rum/defcc map-component < rum/static lc/parser-type-mixin-context (rum/local init-state)
  [component unit-tree app-state]
  (if (= (:mode app-state) :write)
    (render-write-mode component unit-tree app-state)
    (render-read-mode component unit-tree app-state)))

(registry/register-component-info
 :orgpad/map-view
 { :orgpad/default-view-info   { :orgpad/view-type :orgpad/map-view
                                 :orgpad/view-name "default" }
   :orgpad/child-default-view-info     { :orgpad/view-type :orgpad/map-tuple-view
                                         :orgpad/view-name "default" }
   :orgpad/class               map-component
   :orgpad/query               { :unit (qs/unit-query nil)
                                 :view (qs/unit-view-query nil)
                                 :child-props (qs/unit-map-child-view-query nil) }
   :orgpad/child-props-type    :orgpad.map-view/props
   :orgpad/child-props-default { :orgpad/view-type :orgpad.map-view/props
                                 :orgpad/view-name "default"
                                 :orgpad/unit-width 250
                                 :orgpad/unit-height 60
                                 :orgpad/unit-border-color "#009cff"
                                 :orgpad/unit-bg-color "#ffffff"
                                 :orgpad/unit-border-width 2
                                 :orgpad/unit-corner-x 5
                                 :orgpad/unit-corner-y 5
                                 :orgpad/unit-border-dash "1,0" }
   :orgpad/needs-children-info true
   :orgpad/view-name           "Map View"
  })

(rum/defcc map-tuple-component < rum/static lc/parser-type-mixin-context
  [component unit-tree]

  )

(registry/register-component-info
 :orgpad/map-tuple-view
 {
   :orgpad/default-view-info   { :orgpad/view-type :orgpad/map-tuple-view
                                 :orgpad/view-name "default"
                                 :orgpad/active-unit 0 }
   :orgpad/child-default-view-info   { :orgpad/view-type :orgpad/atomic-view
                                       :orgpad/view-name "default" }
   :orgpad/class               map-tuple-component
   :orgpad/query               { :unit (qs/unit-query nil)
                                 :view (qs/unit-map-tuple-view-query nil) }
   :orgpad/needs-children-info true
   :orgpad/view-name           "Map Tuple View"

   :orgpad/propagate-props-from-childs true
  })
