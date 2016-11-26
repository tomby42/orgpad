(ns ^{:doc "Graphics componets"}
  orgpad.components.graphics.primitives
  (:require-macros [orgpad.tools.colls :refer [>-]])
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.tools.rum :as trum]
            [orgpad.tools.css :as css]))

(defn- comp-border-width
  [style]
  (or (-> style :canvas :lineWidth) 0))

(defn- proj
  [i p]
  (nth p i))

(defn- proj-x
  [f pts idx]
  (apply f (map (partial proj idx) pts)))

(defn- proj-min
  [pts idx]
  (proj-x min pts idx))

(defn- proj-max
  [pts idx]
  (proj-x max pts idx))

(defn- comp-bb
  [border-width pts]
  [[(- (proj-min pts 0) border-width) (- (proj-min pts 1) border-width)]
   [(+ (proj-max pts 0) border-width) (+ (proj-max pts 1) border-width)]])

(defn- dims
  [border-width pts]
  (let [bb (comp-bb border-width pts)]
    [(inc (- (>- bb 1 0) (>- bb 0 0)))
     (inc (- (>- bb 1 1) (>- bb 0 1))) ]))

(defn- left-top-corner
  [pts]
  [(proj-min pts 0) (proj-min pts 1)])

(defn- set-style!
  [ctx style]
  (doseq [[attr val] style]
    (aset ctx (name attr) val)))

(defn- lp
  "Compute local point coordinate"
  [p c idx border-width]
  (+ (- (nth p idx) c) border-width))

(defn- pt-lp
  [p l t border-width]
  [(lp p l 0 border-width) (lp p t 1 border-width)])

(defn- get-context
  [state]
  (-> state (trum/ref :canvas) (.getContext "2d")))

(defn- draw-curve
  [state f & [pts]]
  (let [ctx (get-context state)
        args (state :rum/args)
        style (last args)
        border-width (comp-border-width style)
        pts (or pts (subvec args 0 (dec (count args))))
        [l t] (left-top-corner pts)
        [w h] (dims border-width pts)]
    (.clearRect ctx 0 0 w h)
    (set-style! ctx (:canvas style))
    (doto ctx
      (.setLineDash (or (-> style :canvas :lineDash) #js []))
      .beginPath)
    (f ctx pts border-width l t)
    (.stroke ctx)))

(defn- draw-line
  [state]
  (draw-curve state
              (fn [ctx [start end] border-width l t]
                (let [s (pt-lp start l t border-width)
                      e (pt-lp end l t border-width)]
                  (doto ctx
                    (.moveTo (s 0) (s 1))
                    (.lineTo (e 0) (e 1)))))))

(defn- render-curve
  [style & pts]
  (let [border-width (comp-border-width style)
        [w h] (dims border-width pts)
        [l t] (left-top-corner pts)
        style (merge (or (:css style) {}) (css/transform { :translate [l t] }))]
    [ :canvas { :className "graphics primitive" :width w  :height h :style style :ref "canvas"} ]))

(rum/defc line < rum/static (trum/gen-update-mixin draw-line)
  [start end style]
  (render-curve style start end))

(rum/defc poly-line < rum/static (trum/gen-update-mixin draw-poly-line)
  [pts style]
  (apply render-curve style pts))

(defn- draw-quadratic-curve
  [state]
  (draw-curve state
              (fn [ctx [start end ctl-pt] border-width l t]
                (let [s (pt-lp start l t border-width)
                      e (pt-lp end l t border-width)
                      c (pt-lp ctl-pt l t border-width)]
                  (doto ctx
                    (.moveTo (s 0) (s 1))
                    (.quadraticCurveTo (c 0) (c 1) (e 0) (e 1)))))))

(rum/defc quadratic-curve < rum/static (trum/gen-update-mixin draw-quadratic-curve)
  [start end ctl-pt style]
  (render-curve style start end ctl-pt))
