(ns ^{:doc "Sidebar component"}
  orgpad.components.sidebar.sidebar
  (:require [rum.core :as rum]
            [orgpad.tools.sablono :as html]
            [orgpad.tools.js-events :as jev]))

(rum/defcs sidebar-component < (rum/local false)
  [{:keys [rum/react-component rum/local]} position & children]
  [:div {:className (str "sidebar " (if (= position :left) "left " "right "))}
   [:div {:className (str (if (= position :left) "left " "right ") (if @local "sidebar-visible" ""))}
    [:div {:className "sidebar-button"
           :key "sidebar-button"
           :onMouseDown jev/stop-propagation
           ;; :onMouseUp jev/stop-propagation
           :onWheel jev/stop-propagation
           :onClick
           (fn [ev]
             (swap! local not)
             (.stopPropagation ev))}
     [:i {:className (if (not= @local (= position :left))
                       "fa fa-angle-right fa-lg"
                       "fa fa-angle-left fa-lg")}]]
    [:div {:key "sidebar-childrens"
           :onMouseDown jev/stop-propagation
           ;; :onMouseUp jev/stop-propagation
           :onWheel jev/stop-propagation}
     (when (and @local children)
       (html/shtml children))]]])
