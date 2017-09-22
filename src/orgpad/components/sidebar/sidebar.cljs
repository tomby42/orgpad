(ns ^{:doc "Sidebar component"}
  orgpad.components.sidebar.sidebar
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.tools.js-events :as jev]))

(rum/defcs sidebar-component < (rum/local false)
  [{:keys [rum/react-component rum/local]} position & children]
  [ :div { :className (str "sidebar " (if (= position :left) "left " "right ")) }
   [ :div { :className (str (if (= position :left) "left " "right ") (if @local "sidebar-visible" "")) }
    [ :div { :className "sidebar-button"
             :key "sidebar-button"
             :onMouseDown jev/stop-propagation
             :onClick
             (fn [ev]
               (swap! local not)
               (.stopPropagation ev)) }
     [ :i { :className (if (not= @local (= position :left))
                         "fa fa-angle-right fa-lg"
                         "fa fa-angle-left fa-lg") } ] ]
    [ :div { :key "sidebar-childrens" }
      (html children) ] ] ] )
