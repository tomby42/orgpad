(ns ^{:doc "Root component"}
  orgpad.components.root.status
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [cljsjs.react-select]
            [orgpad.cycle.life :as lc]
            [orgpad.tools.dscript :as ds]
            [orgpad.components.registry :as registry]
            [orgpad.tools.rum :as trum]))

(def ^:private mode-icons
  { :read  "fa-eye"
    :write "fa-pencil" })

(defn- next-mode
  [current-mode]
  (case current-mode
    :read  :write
    :write :read))

(defn- list-of-view-names
  [unit]
  (->> unit
       :orgpad/props-refs
       (filter :orgpad/view-name)
       (map :orgpad/view-name)
       (cons "default")
       set
       (map (fn [n] #js { :value n :label n }))
       into-array))

(defn- render-view-names
  [unit view]
  (let [current-name (view :orgpad/view-name)
        list-of-view-names (list-of-view-names unit)]
    (js/React.createElement js/Select
                            #js { :value current-name
                                  :options list-of-view-names
                                 })))

(rum/defcc status < (rum/local false) lc/parser-type-mixin-context
  [component { :keys [unit view path-info] } app-state]
  (let [id (unit :db/id)
        local-state (trum/comp->local-state component)]
    [ :div { :className "status-menu" }
     [ :div { :className "tools-menu" :title "Actions" }
      [ :div { :className "tools-button" :onClick #(swap! local-state not) }
       [ :i { :className "fa fa-navicon fa-lg" } ] ]
      [ :div { :className (str "tools" (when @local-state " more-3")) }
       [ :div { :className "view-name" }
        (render-view-names unit view) ]
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
