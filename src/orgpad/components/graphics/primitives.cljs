(ns ^{:doc "Graphics componets"}
  orgpad.components.graphics.primitives
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.tools.css :as css]))

(defn gen-canvas-mixin
  [update-fn]
  { :did-mount
     (fn [state]
       (update-fn state)
       state)

    :did-update
      (fn [state]
        (update-fn state)
        state) })

(defn- comp-border-width
  [style]
  (or (-> style :canvas :lineWidth) 0))

(defn- dims
  [start end border-width]
  (let [border-width2 (* 2 border-width)]
    [(+ (inc (js/Math.abs (- (end 0) (start 0)))) border-width2)
     (+ (inc (js/Math.abs (- (end 1) (start 1)))) border-width2)]))

(defn- left-top-corner
  [start end]
  [(js/Math.min (start 0) (end 0)) (js/Math.min (start 1) (end 1))])

(defn- set-style!
  [ctx style]
  (doseq [[attr val] style]
    (aset ctx (name attr) val)))

(defn- lp
  "compute local point coordinate"
  [p c idx border-width]
  (+ (- (nth p idx) c) border-width))

(defn- draw-line
  [state]
  (let [ctx (-> state :rum/react-component (aget "refs") (aget "canvas") (.getContext "2d"))
        [start end style] (state :rum/args)
        border-width (comp-border-width style)
        [l t] (left-top-corner start end)
        [w h] (dims start end border-width)]
    (.clearRect ctx 0 0 w h)
    (set-style! ctx (:canvas style))
    (doto ctx
      (.setLineDash (or (-> style :canvas :lineDash) #js []))
      .beginPath
      (.moveTo (lp start l 0 border-width) (lp start t 1 border-width))
      (.lineTo (lp end l 0 border-width) (lp end t 1 border-width))
      .stroke)))

(rum/defc line < rum/static (gen-canvas-mixin draw-line)
  [start end style]
  (let [border-width (comp-border-width style)
        [w h] (dims start end border-width)
        [l t] (left-top-corner start end)
        style (merge (or (:css style) {}) (css/transform { :translate [l t] }))]
    [ :canvas { :className "graphics primitive" :width w  :height h :style style :ref "canvas"} ]))
