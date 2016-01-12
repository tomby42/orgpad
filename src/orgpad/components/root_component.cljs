(ns ^{:doc "Root component"}
  orgpad.components.root-component
  (:require [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [orgpad.components.queries :as qs]
            [orgpad.components.registry :as registry]))

(defui RootComponent
  static om/IQuery
  (query
   [this]
   [{:orgpad/root-view [{:unit qs/unit-query}
                        {:view qs/unit-view-query}]}])

  Object
  (render
   [this]
   (let [{:keys [orgpad/root-view]} (om/props this)
         child-info (-> root-view :orgpad/unit-view :view
                        :orgpad/view-type registry/get-component-info)
         child-factory (child-info :orgpad/factory)]

     (dom/div
      #js {:className "root-view"}
      (child-factory root-view)
      ))))


(registry/register-component-info
 :root-component
 {:orgpad/default-view-info   {:orgpad/view-type :atomic-view}
  :orgpad/class               RootComponent
  :orgpad/factory             (om/factory RootComponent)
  :orgpad/needs-children-info true
  })
