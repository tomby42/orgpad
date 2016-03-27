(ns ^{:doc "Root component"}
  orgpad.components.root.component
  (:require [rum.core :as rum]
            [orgpad.cycle.life :as lc]
            [orgpad.components.registry :as registry]
            [orgpad.components.node :as node]
            [orgpad.components.sidebar.sidebar :as sidebar]))

(rum/defcc root-component < lc/parser-type-mixin-context [component]
  (let [unit-tree (lc/props component :orgpad/root-view [])]
    [ :div { :className "root-view" }
      (rum/with-key (sidebar/sidebar-component) 0)
      (rum/with-key (node/node unit-tree) 1) ] ) )

(registry/register-component-info
 :orgpad/root-view
 {:orgpad/default-view-info   {:orgpad/view-type :orgpad/atomic-view}
  :orgpad/class               root-component
  :orgpad/needs-children-info true
  })
