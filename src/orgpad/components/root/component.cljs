(ns ^{:doc "Root component"}
  orgpad.components.root.component
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.cycle.life :as lc]
            [orgpad.components.registry :as registry]
            [orgpad.components.node :as node]
            [orgpad.components.sidebar.sidebar :as sidebar]
            [orgpad.components.root.mode-selector :as ms]))

(rum/defcc root-component < lc/parser-type-mixin-context [component]
  (let [unit-tree (lc/props component :orgpad/root-view [])
        app-state (lc/props component :orgpad/app-state [])]
    [ :div { :className "root-view" }
      (rum/with-key (sidebar/sidebar-component) 0)
      (rum/with-key (node/node unit-tree app-state) 1)
      (rum/with-key (ms/mode-selector app-state) 2) ] ) )

(registry/register-component-info
 :orgpad/root-view
 {:orgpad/default-view-info   {:orgpad/view-type :orgpad/atomic-view}
  :orgpad/class               root-component
  :orgpad/needs-children-info true
  })
