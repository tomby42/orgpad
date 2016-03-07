(ns ^{:doc "Atomic component"}
  orgpad.components.atomic.component
  (:require [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [clojure.set :as s]
            [orgpad.tools.macros :as m :refer-macros [bind]]
            [orgpad.components.queries :as qs]
            [orgpad.components.registry :as registry]
            [orgpad.components.atomic.atom-editor :as atom-editor]
            [orgpad.components.atomic.tags-editor :as tags-editor]
            [orgpad.components.atomic.desc-editor :as desc-editor]))

(defui AtomicComponent
  static om/IQuery
  (query
   [this]
   [{:unit (qs/unit-query nil)}
    {:view (qs/unit-view-query nil)}])

  Object
  (onTagsChange
   [this new-tags]
   (let [{:keys [unit]}  (om/props this)
         tags               (unit :orgpad/tags)
         id                 (unit :db/id)
         removed-tags       (s/difference tags new-tags)
         added-tags         (s/difference new-tags tags)]
     (if (-> removed-tags empty? not)
       (om/transact! this `[(tags/remove {:db/id ~id :orgpad/tags ~removed-tags})]))
     (if (-> added-tags empty? not)
       (om/transact! this `[(tags/add {:db/id ~id :orgpad/tags ~added-tags})]))
     )
   )

  (render
   [this]
   (let [{:keys [unit]} (om/props this)]
     (apply
      dom/div
      nil
      [(desc-editor/desc-editor {:desc (unit :orgpad/desc)
                                 :id (unit :db/id)})
       (atom-editor/atom-editor {:atom (unit :orgpad/atom)
                                 :id (unit :db/id)})
       (tags-editor/tags-editor {:tags (unit :orgpad/tags)
                                 :id (unit :db/id)
                                 :on-change (bind onTagsChange)})])))
  )


(registry/register-component-info
 :orgpad/atomic-view
 {:orgpad/default-view-info   {:orgpad/view-type :orgpad/atomic-view}
  :orgpad/class               AtomicComponent
  :orgpad/factory             (om/factory AtomicComponent)
  :orgpad/needs-children-info false
  :orgpad/view-name           "Atomic View"
  })
