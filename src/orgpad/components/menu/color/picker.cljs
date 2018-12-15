(ns ^{:doc "Color picker menu"}
  orgpad.components.menu.color.picker
  (:require [rum.core :as rum]
            [goog.events :as gev]
            [goog.ui.Component.EventType :as gevt]
            [goog.ui.HsvaPalette]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.tools.js-events :as jev]
            [orgpad.tools.math :as math]
            [orgpad.tools.rum :as trum]
            [orgpad.effects.core :as eff]))

(defn- transfer-palette
  [old new]
  (-> new
      (assoc :palette (old :palette))
      (assoc :lid (old :lid))))

(def ^:private remount
  {:did-remount transfer-palette
   :transfer-state transfer-palette})

(defn- create-color-picker
  [state]
  (let [pal (js/goog.ui.HsvaPalette. nil, nil, nil, "goog-hsva-palette-sm")
        dom-node (trum/ref-node state :color-picker)
        component (state :rum/react-component)]
    (.render pal dom-node)
    (let [lid (js/goog.events.listen pal js/goog.ui.Component.EventType.ACTION
                                     (fn [e]
                                       (let [args (-> component rum/state deref :rum/args)
                                             on-change (nth args 2)
                                             color (nth args 0)
                                             new-color (-> e .-target .getColorRgbaHex)]
                                         (when (and color (not= color new-color))
                                           (on-change new-color)))))]
      (-> state
          (assoc :palette pal)
          (assoc :lid lid)))))

(defn- ->rgba
  [color]
  (if color
    (if (< (.-length color) 8)
      (str color "ff")
      color)
    "ffffffff"))

(defn- update-color
  [state]
  (let [color (-> state :rum/args first)
        pal   (state :palette)]
    (.setColorRgbaHex pal (->rgba color))))

(defn- destroy-color-picker
  [state]
  (let [pal (state :palette)
        lid (state :lid)]
    (js/goog.events.unlistenByKey lid)
    (-> state
        (dissoc :palette)
        (dissoc :lid))))

(rum/defc color-picker < rum/static (trum/gen-reg-mixin create-color-picker destroy-color-picker)
  (trum/gen-update-mixin update-color) remount
  [color props on-change]
  [:div (merge props {:ref "color-picker"
                      :onMouseDown jev/stop-propagation
                      :onMouseUp jev/stop-propagation})])
