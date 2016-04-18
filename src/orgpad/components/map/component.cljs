(ns ^{:doc "Map component"}
  orgpad.components.map.component
  (:require [rum.core :as rum]
            [orgpad.cycle.life :as lc]
            [orgpad.components.queries :as qs]
            [orgpad.components.registry :as registry]
            [orgpad.components.menu.circle :as cm]))

(rum/defcc map-component < rum/static lc/parser-type-mixin-context [component unit-tree]
  (let [{:keys [unit view]} unit-tree]
    [ :div { :className "map-view" }
     [ :div { :className "map-view-canvas" }
      ]
     ]
    ))

(registry/register-component-info
 :orgpad/map-view
 { :orgpad/default-view-info   { :orgpad/view-type :orgpad/map-tuple-view
                                 :orgpad/view-name "default" }
   :orgpad/class               map-component
   :orgpad/query               { :unit (qs/unit-query nil)
                                 :view (qs/unit-view-query nil) }
   :orgpad/needs-children-info true
   :orgpad/view-name           "Map View"
  })

(rum/defcc map-tuple-component < rum/static lc/parser-type-mixin-context [component unit-tree]

  )


(registry/register-component-info
 :orgpad/map-tuple-view
 { :orgpad/default-view-info   { :orgpad/view-type :orgpad/atomic-view
                                 :orgpad/view-name "default" }
   :orgpad/class               map-tuple-component
   :orgpad/query               { :unit (qs/unit-query nil)
                                 :view (qs/unit-view-query nil) }
   :orgpad/needs-children-info true
   :orgpad/view-name           "Map Tuple View"

   :orgpad/propagated-prop-from-childs [ :orgpad/width :orgpad/height ]
   :orgpad/propagate-child-choosed-prop [ :orgpad/active-unit ]
  })
