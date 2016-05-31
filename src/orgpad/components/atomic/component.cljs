(ns ^{:doc "Atomic component"}
  orgpad.components.atomic.component
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.cycle.life :as lc]
            [orgpad.components.queries :as qs]
            [orgpad.components.registry :as registry]
            [orgpad.components.menu.circle :as mc]
            [orgpad.components.atomic.atom-editor :as atom-editor]
            [orgpad.components.atomic.tags-editor :as tags-editor]
            [orgpad.components.atomic.desc-editor :as desc-editor]))

(defn- render-write-mode
  [{:keys [unit]} app-state]
  [ :div { :className "atomic-view" }
    (rum/with-key ( desc-editor/desc-editor (unit :db/id) (unit :orgpad/desc) ) 0)
    (rum/with-key ( tags-editor/tags-editor (unit :db/id) (unit :orgpad/tags) ) 1)
    (rum/with-key ( atom-editor/atom-editor (unit :db/id) (unit :orgpad/atom) ) 2)
   ] )

(defn- render-read-mode
  [{:keys [unit]} app-state]
    [ :div { :className "atomic-view" }
      (when (and (unit :orgpad/desc) (not= (unit :orgpad/desc) ""))
        [ :div { :key 0 } (unit :orgpad/desc)])
      (when (and (unit :orgpad/tags) (not= (unit :orgpad/tags) []))
        [ :div { :key 1} [ :div {} (html (into [] (map-indexed (fn [idx tag] (html [ :span { :key idx :className "react-tagsinput-tag" } tag ])) (unit :orgpad/tags)))) ] ])
      (when (and (unit :orgpad/atom) (not= (unit :orgpad/atom) ""))
        [ :div  {:dangerouslySetInnerHTML
                 {:__html (unit :orgpad/atom)} } ])
     ])

(rum/defc atomic-component < rum/static lc/parser-type-mixin-context
  [unit-tree app-state]
  (if (= (:mode app-state) :write)
    (render-write-mode unit-tree app-state)
    (render-read-mode unit-tree app-state)))

(registry/register-component-info
 :orgpad/atomic-view
 { :orgpad/default-view-info   { :orgpad/view-type :orgpad/atomic-view
                                 :orgpad/view-name "default" }
   :orgpad/class               atomic-component
   :orgpad/needs-children-info false
   :orgpad/view-name           "Atomic View"
  })
