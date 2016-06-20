(ns ^{:doc "Root component"}
  orgpad.components.root.status
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.cycle.life :as lc]
            [orgpad.tools.rum :as trum]))

(def ^:private mode-icons
  { :read  "fa-eye"
    :write "fa-pencil" })

(defn- next-mode
  [current-mode]
  (case current-mode
    :read  :write
    :write :read))

(rum/defcc status < (rum/local false) lc/parser-type-mixin-context
  [component { :keys [unit view path-info] } app-state]
  (let [id (unit :db/id)
        local-state (trum/comp->local-state component)]
    [ :div { :className "status-menu" }
     [ :div { :className "tools-menu" :title "Actions" }
      [ :div { :className "tools-button" :onClick #(swap! local-state not) }
       [ :i { :className "fa fa-navicon fa-lg" } ] ]
      [ :div { :className (str "tools" (when @local-state " more-3")) }
       [ :div { :className "tools-button" }
        [ :i { :className "fa fa-leaf fa-lg" } ] ]
       [ :div { :className "tools-button" }
        [ :i { :className "fa fa-leaf fa-lg" } ] ]
       [ :div { :className "tools-button" }
        [ :i { :className "fa fa-leaf fa-lg" } ] ]

       ]
      ]

     [ :div { :className "mode-button"
              :title "Toggle mode"
              :onClick #(lc/transact!
                        component
                        [[:orgpad/app-state
                          [[:mode] (next-mode (:mode app-state))]]]) }
      [ :i { :className (str "fa "  (mode-icons (:mode app-state)) " fa-lg") } ] ]

     (when (not= id 0)
       [ :div { :className "done-root-unit-button"
                :title "Done"
                :onClick #(lc/transact!
                          component
                          [[:orgpad/root-unit-close { :db/id id
                                                      :orgpad/view-name (view :orgpad/view-name)
                                                      :orgpad/view-type (view :orgpad/view-type)
                                                      :orgpad/view-path (path-info :orgpad/view-path) }]])}
        [ :i { :className "fa fa-check-circle-o fa-lg" } ] ] ) ] ) )
