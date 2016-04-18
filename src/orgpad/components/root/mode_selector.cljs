(ns ^{:doc "Root component"}
  orgpad.components.root.mode-selector
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.cycle.life :as lc]
            [orgpad.components.registry :as registry]
            [orgpad.components.node :as node]
            [orgpad.components.sidebar.sidebar :as sidebar]))

(def ^:private mode-icons
  { :read  "fa-eye"
    :write "fa-pencil" })

(defn- next-mode
  [current-mode]
  (case current-mode
    :read  :write
    :write :read))

(rum/defcc mode-selector < lc/parser-type-mixin-context [component app-state]
  [ :div { :className "mode-button" }
    [ :i { :className (str "fa "  (mode-icons (:mode app-state)) " fa-2x")
           :onClick #(lc/transact!
                      component
                      [[:orgpad/app-state
                        [[:mode] (next-mode (:mode app-state))]]]) } ] ])
