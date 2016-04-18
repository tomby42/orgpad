(ns ^{:doc "Atomic component"}
  orgpad.components.atomic.component
  (:require [rum.core :as rum]
            [orgpad.cycle.life :as lc]
            [orgpad.components.queries :as qs]
            [orgpad.components.registry :as registry]
            [orgpad.components.menu.circle :as mc]
            [orgpad.components.atomic.atom-editor :as atom-editor]
            [orgpad.components.atomic.tags-editor :as tags-editor]
            [orgpad.components.atomic.desc-editor :as desc-editor]))


(rum/defc atomic-component < rum/static lc/parser-type-mixin-context [unit-tree app-state]
  (let [{:keys [unit]} unit-tree]
    [ :div { :className "atomic-view" }
      (rum/with-key ( desc-editor/desc-editor (unit :db/id) (unit :orgpad/desc) ) 0)
      (rum/with-key ( tags-editor/tags-editor (unit :db/id) (unit :orgpad/tags) ) 1)
      (rum/with-key ( atom-editor/atom-editor (unit :db/id) (unit :orgpad/atom) ) 2)
     ] ) )

(registry/register-component-info
 :orgpad/atomic-view
 { :orgpad/default-view-info   { :orgpad/view-type :orgpad/atomic-view
                                 :orgpad/view-name "default" }
   :orgpad/class               atomic-component
   :orgpad/query               { :unit (qs/unit-query nil)
                                 :view (qs/unit-view-query nil) }
   :orgpad/needs-children-info false
   :orgpad/view-name           "Atomic View"
  })
