(ns ^{:doc "Root component"}
  orgpad.components.root.component
  (:require [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [orgpad.components.queries :as qs]
            [orgpad.components.registry :as registry]
            [orgpad.components.node :as node]
            [orgpad.components.sidebar.sidebar :as sidebar]))

(defui RootComponent
  static om/IQuery
  (query
   [this]
   [{:orgpad/root-view (om/get-query node/Node)}])

  Object
  (render
   [this]
   (let [{:keys [orgpad/root-view]} (om/props this)
         ]

     (apply
      dom/div
      #js {:className "root-view"}
      [
       (sidebar/sidebar-component)
       (node/node root-view)
       ]
      ))
   )

)

(registry/register-component-info
 :orgpad/root-view
 {:orgpad/default-view-info   {:orgpad/view-type :orgpad/atomic-view}
  :orgpad/class               RootComponent
  :orgpad/factory             (om/factory RootComponent)
  :orgpad/needs-children-info true
  })
