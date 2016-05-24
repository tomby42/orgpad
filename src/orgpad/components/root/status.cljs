(ns ^{:doc "Root component"}
  orgpad.components.root.status
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.cycle.life :as lc]))

(def ^:private mode-icons
  { :read  "fa-eye"
    :write "fa-pencil" })

(defn- next-mode
  [current-mode]
  (case current-mode
    :read  :write
    :write :read))

(rum/defcc status < lc/parser-type-mixin-context [component { :keys [unit view path-info] } app-state]
  (let [id (unit :db/id)]
    [ :div
     [ :div { :className "mode-button" }
      [ :i { :className (str "fa "  (mode-icons (:mode app-state)) " fa-lg")
             :title (if (= (:mode app-state) :read) "Read mode" "Write mode")
             :onClick #(lc/transact!
                        component
                        [[:orgpad/app-state
                          [[:mode] (next-mode (:mode app-state))]]]) } ] ]

     (when (not= id 0)
       [ :div { :className "close-root-unit-button" }
        [ :i { :className "fa fa-close fa-lg"
               :title "Close"
               :onClick #(lc/transact!
                          component
                          [[:orgpad/root-unit-close { :db/id id
                                                      :orgpad/view-name (view :orgpad/view-name)
                                                      :orgpad/view-type (view :orgpad/view-type)
                                                      :orgpad/view-path (path-info :orgpad/view-path) }]]) } ] ] ) ] ) )
