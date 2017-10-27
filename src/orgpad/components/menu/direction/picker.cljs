(ns ^{:doc "Direction picker"}
  orgpad.components.menu.direction.picker
  (:require [rum.core :as rum]
            [orgpad.tools.js-events :as jev]
            [orgpad.tools.math :as math]
            [orgpad.tools.rum :as trum]
            [orgpad.effects.core :as eff]))

(defn- gen-button [valueChange currentValue value style]
  [ :div
     {:className (if (= currentValue value) "direction-button active" "direction-button")
      :onMouseDown jev/block-propagation
      :onClick (jev/make-block-propagation #(valueChange value))
      :key value}
      [:i {:className (str "fa " style)}]])

(rum/defc direction-picker < rum/static
  [valueChange value]
  (let [gen-button' (partial gen-button valueChange value)]
  [ :div.direction-picker {} 
    [ (gen-button' :topleft "fa-arrow-left rotate-45-right") 
      (gen-button' :top "fa-arrow-up") 
      (gen-button' :topright "fa-arrow-up rotate-45-right") 
      (gen-button' :left "fa-arrow-left") 
      (gen-button' :center "fa-circle") 
      (gen-button' :right "fa-arrow-right") 
      (gen-button' :bottomleft "fa-arrow-down rotate-45-right") 
      (gen-button' :bottom "fa-arrow-down") 
      (gen-button' :bottomright "fa-arrow-right rotate-45-right")]])) 
