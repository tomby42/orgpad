(ns ^{:doc "Root component"}
  orgpad.components.root.component
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.tools.rum :as trum]
            [orgpad.cycle.life :as lc]
            [orgpad.components.registry :as registry]
            [orgpad.components.node :as node]
            [orgpad.components.sidebar.sidebar :as sidebar]
            [orgpad.components.root.status :as st]
            [orgpad.tools.orgpad :as ot]))

(rum/defcc root-component < lc/parser-type-mixin-context (rum/local nil)
  [component]
  (let [unit-tree (lc/query component :orgpad/root-view [])
        app-state (lc/query component :orgpad/app-state [])
        local-state (trum/comp->local-state component)] ;; local-state contains children component or nil

    ;; TODO: hack!! We need to think about passing custom params to children and/or local states in app state
    ;; regarding to render hierarchy.
    (js/setTimeout #(let [c (lc/get-global-cache component (ot/uid unit-tree) "component")]
                      (when (and c (.-context c) (not= @local-state c))
                        (js/console.log "updating child component" c)
                        (reset! local-state c))) 0)

    [ :div { :className "root-view" }
      ;; (rum/with-key (sidebar/sidebar-component) 0)
      (rum/with-key (node/node unit-tree app-state) "root-view-part")
      (rum/with-key (st/status unit-tree app-state) "status-part")
      (when (app-state :loading)
        [ :div.loading
         [ :div.status
          [ :i.fa.fa-spinner.fa-pulse.fa-3x.fa-fw.margin-bottom ]
          [ :div.sr-only "Loading..." ] ]
         ]
        )
     ] ) )

(registry/register-component-info
 :orgpad/root-view
 {:orgpad/default-view-info   { :orgpad/view-type :orgpad/map-view
                                :orgpad/view-name "default" }
  :orgpad/class               root-component
  :orgpad/needs-children-info true
  })
