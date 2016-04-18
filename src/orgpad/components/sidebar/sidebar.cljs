(ns ^{:doc "Sidebar component"}
  orgpad.components.sidebar.sidebar
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]))

(rum/defcs sidebar-component < (rum/local false) [{:keys [rum/react-component rum/local]} & children]
  [ :div { :className "sidebar" }
   [ :div { :className (if @local "sidebar-visible right" "right") }
    [ :div { :className "sidebar-button"
             :key "sidebar-button"
             :onClick
             (fn [_]
               (swap! local not) ) }
      [ :i { :className (if @local "fa fa-angle-right fa-3x" "fa fa-angle-left fa-3x") } ] ]
    [ :div { :key "sidebar-childrens" }
      (html children) ] ] ] )
