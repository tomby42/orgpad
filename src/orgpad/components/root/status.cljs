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
  [unit view-type]
  (->> unit
       :orgpad/props-refs
       (filter :orgpad/view-name)
       (filter #(= (% :orgpad/view-type) view-type))
       (map :orgpad/view-name)
       (cons "default")
       set
       (map (fn [n] #js { :value n :label n }))
       into-array))

(defn- render-view-names
  [component {:keys [unit view] :as unit-tree} local-state]
  (let [current-name (view :orgpad/view-name)
        list-of-view-names (list-of-view-names unit (view :orgpad/view-type))]
    (js/React.createElement js/Select
                            #js { :value current-name
                                  :options list-of-view-names
                                  :onInputChange #(do (swap! local-state merge { :typed % }) %)
                                  :onChange (fn [ev]
                                              (lc/transact! component
                                                            [[:orgpad/root-view-conf [unit-tree
                                                                                      { :attr :orgpad/view-name
                                                                                        :value (.-value ev) }]]]))
                                 })))

(defn- list-of-view-types
  []
  (->> (dissoc (registry/get-registry) :orgpad/root-view)
       (map (fn [[view-type info]] #js { :key view-type
                                         :value (info :orgpad/view-name)
                                         :label (info :orgpad/view-name) }))
       into-array))

(defn- render-view-types
  [component {:keys [view] :as unit-tree}]
  (let [list-of-view-types (list-of-view-types)
        current-type (-> view :orgpad/view-type registry/get-component-info :orgpad/view-name)]
    (js/React.createElement js/Select
                            #js { :value current-type
                                  :options list-of-view-types
                                  :clearable false
                                  :searchable false
                                  :onChange (fn [ev]
                                              (lc/transact! component
                                                            [[:orgpad/root-view-conf [unit-tree
                                                                                      { :attr :orgpad/view-type
                                                                                        :value (.-key ev) }]]]))
                                 })))

(rum/defcc status < (rum/local { :unroll false :view-menu-unroll false :typed "" } ) lc/parser-type-mixin-context
  [component { :keys [unit view path-info] :as unit-tree } app-state]
  (let [id (unit :db/id)
        local-state (trum/comp->local-state component)]
    [ :div { :className "status-menu" }
     [ :div { :className "tools-menu" :title "Actions" }
      [ :div { :className "tools-button" :onClick #(swap! local-state update-in [:unroll] not) }
       [ :i { :className "fa fa-navicon fa-lg" } ] ]
      [ :div { :className (str "tools" (when (@local-state :unroll) " more-current")) }
       [ :div { :className "view-menu" }
        [ :span { :className "menu-header"
                  :onClick #(swap! local-state update-in [:view-menu-unroll] not) }
         [ :span {} "View " [ :i {:className (str "fa " (if (@local-state :view-menu-unroll) "fa-caret-up" "fa-caret-down")) }] ] ]
        [ :ul { :className (str "menu-body " (when (@local-state :view-menu-unroll) "open")) }
         [ :li
          [ :div { :className "view-name" }
           (render-view-names component unit-tree local-state)
           [ :span { :className "fa fa-plus-circle view-name-add"
                     :title "New view"
                     :onClick #(lc/transact! component
                                             [[:orgpad/root-new-view [unit-tree
                                                                      { :attr :orgpad/view-name
                                                                        :value (@local-state :typed) }]]]) } ] ] ]
         [ :li
          [ :div { :className "view-type" }
           (render-view-types component unit-tree) ] ] ] ]

        [ :div { :className "mode-button" }
          [ :i { :className (str "fa fa-leaf fa-lg") } ] ]
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
