(ns ^{:doc "Color picker menu"}
  orgpad.components.menu.color.react-color-picker
  (:require [rum.core :as rum]
            [cljsjs.react]
            [cljsjs.react-color]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.tools.js-events :as jev]
            [orgpad.tools.rum :as trum]
            ))

(defn- transfer-palette
  [old new]
  (-> new
      (assoc :palette (old :palette))
      (assoc :lid (old :lid))))

(def ^:private remount
  {:did-remount transfer-palette
   :transfer-state transfer-palette})

(defn js-color->hex 
    [js-color]
    (let [clr (js->clj (.-rgb js-color))
          a (get clr "a")                           ;a is not hexadecimal, but is [0-1] => must be converted
          a (int (* 255 a))                         ;casted to int to round 
          a (-> a clj->js (.toString "16"))         ;was not able to find ClujureScript solution
          a (if (< (.-length a) 2) (str "0" a) a)   ;a can be <16 => can have only one char
          hex (str (.-hex js-color) a)]
      hex))
 
(defn- create-color-picker
  [state]
  (let [args (:rum/args state)
        color (nth args 0)
        on-change (nth args 2)
        cpick (js/React.createElement js/ReactColor.SketchPicker
                                      #js {:color color
                                           :onChange (fn [c _]
                                                       (on-change (js-color->hex c)))})
        dom-node (trum/ref-node state :color-picker)]
    (.render js/ReactDOM cpick dom-node)
    (-> state
        (assoc :picker cpick))))
  
(defn- destroy-color-picker
  [state]
  (-> state
      (dissoc :picker)))

(rum/defc color-picker < rum/static (trum/gen-reg-mixin create-color-picker destroy-color-picker)
  remount 
  [color props on-change]
  [:div (merge props {:ref "color-picker"})])
