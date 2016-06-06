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

(defn- render-local-menu
  [unit-tree app-state]
  (html
   [ :div {}
    ]))

(defn- render-write-mode
  [component { :keys [unit view props] :as unit-tree } app-state]
  (let [active-child (-> view :orgpad/active-unit)
        child-tree (-> unit :orgpad/refs (get active-child))]
    [ :div { :className "map-tuple" }
      (rum/with-key (render-local-menu unit-tree app-state) 0)
      (when child-tree
        (rum/with-key (node/node child-tree app-state) 1))
     ]))

(defn- render-read-mode
  [component { :keys [unit view props] :as unit-tree } app-state]
  )

(rum/defcc map-tuple-component < rum/static lc/parser-type-mixin-context
  [component unit-tree app-state]
  (if (= (app-state :mode) :write)
    (render-write-mode component unit-tree app-state)
    (render-read-mode component unit-tree app-state)))

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
                                             [:orgpad/unit-width :orgpad/unit-height :orgpad/unit-border-color
                                              :orgpad/unit-bg-color :orgpad/unit-border-width :orgpad/unit-corner-x
                                              :orgpad/unit-corner-y :orgpad/unit-border-style] }
  })
