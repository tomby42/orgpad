(ns ^{:doc "Map component"}
  orgpad.components.map.tuple
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.cycle.life :as lc]
            [orgpad.components.queries :as qs]
            [orgpad.components.registry :as registry]
            [orgpad.components.menu.circle :as mc]
            [orgpad.components.node :as node]
            [orgpad.components.map.unit :as munit]
            [orgpad.tools.css :as css]
            [orgpad.tools.js-events :as jev]
            [orgpad.tools.rum :as trum]))

(defn- new-sheet
  [component unit-tree]
  (lc/transact! component [[ :orgpad.units/new-sheet unit-tree ]]))

(defn- switch-active-sheet
  [component {:keys [unit view]} dir]
  (lc/transact! component [[ :orgpad.sheet/switch-active { :db/id (view :db/id)
                                                           :active (view :orgpad/active-unit)
                                                           :direction dir
                                                           :nof-sheets (-> unit :orgpad/refs count) } ]]))

(defn- render-local-menu
  [component unit-tree app-state local-state]
  (html
   [ :div { :className "map-tuple-menu" }
    [ :div { :className "tools-menu" :title "Actions" }
     [ :div { :className "tools-button" :onClick #(swap! local-state not) }
      [ :i { :className "fa fa-cogs fa-lg" } ] ]
     [ :div { :className (str "tools" (when @local-state " more-4")) }
      [ :div { :className "tools-button" :title "New sheet"
               :onClick #(new-sheet component unit-tree) }
       [ :i { :className "fa fa-plus-square-o fa-lg" } ] ]
      [ :div { :className "tools-button" :title "Previous" }
       [ :i { :className "fa fa-caret-left fa-lg" } ] ]
      [ :div { :className "tools-button" :title "Next"
               :onClick #(switch-active-sheet component unit-tree 1) }
       [ :i { :className "fa fa-caret-right fa-lg" } ] ]
      [ :div { :className "tools-button" :title "Remove" }
       [ :i { :className "fa fa-remove fa-lg" } ] ]
      ]
     ] ] ))

(defn- render-sheet-number
  [{ :keys [unit view]}]
  (html
   [ :div { :className "map-tuple-sheet-number" :key 1 }
    (str (-> view :orgpad/active-unit inc) "/" (-> unit :orgpad/refs count))
    ]
  ))

(defn- render-write-mode
  [component { :keys [unit view props] :as unit-tree } app-state local-state]
  (let [active-child (-> view :orgpad/active-unit)
        child-tree (-> unit :orgpad/refs (get active-child))]
    [ :div { :className "map-tuple" }
      (render-local-menu component unit-tree app-state local-state)
      (render-sheet-number unit-tree)
      (when child-tree
        (rum/with-key (node/node child-tree app-state) 2))
     ]))

(defn- render-read-mode
  [component { :keys [unit view props] :as unit-tree } app-state]
  )

(rum/defcc map-tuple-component < rum/static lc/parser-type-mixin-context (rum/local false)
  [component unit-tree app-state]
  (let [local-state (trum/comp->local-state component)]
    (if (= (app-state :mode) :write)
      (render-write-mode component unit-tree app-state local-state)
      (render-read-mode component unit-tree app-state))))

(registry/register-component-info
 :orgpad/map-tuple-view
 {
   :orgpad/default-view-info   { :orgpad/view-type :orgpad/map-tuple-view
                                 :orgpad/view-name "default"
                                 :orgpad/active-unit 0 }
   :orgpad/child-default-view-info   { :orgpad/view-type :orgpad/atomic-view
                                       :orgpad/view-name "default" }
   :orgpad/class               map-tuple-component
   :orgpad/needs-children-info true
   :orgpad/view-name           "Map Tuple View"

   :orgpad/propagate-props-from-children? true
   :orgpad/propagated-props-from-children { :orgpad.map-view/props
                                             [:orgpad/view-type :orgpad/unit-width :orgpad/unit-height
                                              :orgpad/unit-border-color :orgpad/unit-bg-color
                                              :orgpad/unit-border-width :orgpad/unit-corner-x
                                              :orgpad/unit-corner-y :orgpad/unit-border-style] }
  })
