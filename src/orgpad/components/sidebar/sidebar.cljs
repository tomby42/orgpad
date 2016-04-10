(ns ^{:doc "Sidebar component"}
  orgpad.components.sidebar.sidebar
  (:require [rum.core :as rum]))

(rum/defcs sidebar-component < (rum/local false) [{:keys [rum/react-component rum/local]}]
  (let [classes (if @local "sidebar-visible right" "right")]
    [ :div { :className "sidebar" }
     [ :div { :className classes }
      [ :button { :type "button"
                  :className "sidebar-button"
                  :key "sidebar-button"
                  :onClick
                    (fn [_]
                      (swap! local not) ) } ]
      [ :div { :key "sidebar-childrens" }
        (-> react-component .-props .-children) ] ] ] ) )
