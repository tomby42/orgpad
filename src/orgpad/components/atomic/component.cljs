(ns ^{:doc "Atomic component"}
  orgpad.components.atomic.component
  (:require [rum.core :as rum]
            [orgpad.cycle.life :as lc]
            [orgpad.components.queries :as qs]
            [orgpad.components.registry :as registry]
            [orgpad.components.atomic.atom-editor :as atom-editor]
            [orgpad.components.atomic.tags-editor :as tags-editor]
            [orgpad.components.atomic.desc-editor :as desc-editor]))


(rum/defc atomic-component < rum/static lc/parser-type-mixin-context [unit-tree]
  (let [{:keys [unit]} unit-tree]
    [ :div {}
      ( desc-editor/desc-editor (unit :db/id) (unit :orgpad/desc) )
      ( tags-editor/tags-editor (unit :db/id) (unit :orgpad/tags) )
      ( atom-editor/atom-editor (unit :db/id) (unit :orgpad/atom) ) ] ) )

(registry/register-component-info
 :orgpad/atomic-view
 { :orgpad/default-view-info   { :orgpad/view-type :orgpad/atomic-view }
   :orgpad/class               atomic-component
   :orgpad/query               { :unit (qs/unit-query nil)
                                 :view (qs/unit-view-query nil) }
   :orgpad/needs-children-info false
   :orgpad/view-name           "Atomic View"
  })
