(ns orgpad.components.graphics.primitives-svg
  (:require-macros [orgpad.tools.colls :refer [>-]])
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.tools.rum :as trum]
            [orgpad.tools.geom :refer [++ -- *c normalize] :as geom]
            [orgpad.tools.math :as math]
            [orgpad.tools.bezier :as bez]
            [orgpad.tools.colls :as colls]
            [orgpad.tools.css :as css]
            [orgpad.components.graphics.utils :refer [comp-border-width comp-bb dims
                                                      left-top-corner lp pt-lp
                                                      comp-quad-arrow-pts comp-arc-arrow-pts
                                                      arc-bbox]]))

(defn- render-curve
  [render-fn style & pts]
  (let [border-width (comp-border-width style)
        [w h] (dims border-width pts)
        [l t] (left-top-corner pts)
        border-width (comp-border-width style)
        css-style (merge (or (:css style) {}) (css/transform {:translate [l t]}))]
    [:svg {:className "graphics primitive"
           :width w
           :height h
           :style css-style
           :viewBox (str "0 0 " w " " h)
           :ref "canvas"}
     (if (:params style)
       (render-fn (:params style) (:svg style) l t border-width pts)
       (render-fn (:svg style) l t border-width pts))]))

(defn- draw-line
  [style l t border-width [start end]]
  (let [s (pt-lp start l t border-width)
        e (pt-lp end l t border-width)]
    [:line (merge style
                  {:x1 (s 0)
                   :y1 (s 1)
                   :x2 (e 0)
                   :y2 (e 1)})]))

(rum/defc line < rum/static
  [start end style]
  (render-curve draw-line style start end))

(defn- pp
  [p]
  (str (p 0) "," (p 1)))

(defn- draw-quadratic-curve
  [style l t border-width [start end ctl-pt]]
  (let [s (pt-lp start l t border-width)
        e (pt-lp end l t border-width)
        c (pt-lp ctl-pt l t border-width)]
    [:path (merge style
                  {:d (str "M " (pp s)
                           " Q " (pp c) " " (pp e))})]))

(rum/defc quadratic-curve < rum/static
  [start end ctl-pt style]
  (render-curve draw-quadratic-curve style start end ctl-pt))

(defn- draw-polyline
  [style l t border-width pts]
  (let [s (pt-lp (first pts) l t border-width)]
    [:path (merge style
                  {:d (reduce (fn [s pt]
                                (let [p (pt-lp pt l t border-width)]
                                  (str s " L " (pp p))))
                              (str "M " (pp s)) (rest pts))})]))

(rum/defc poly-line < rum/static
  [pts style]
  (apply render-curve draw-polyline style pts))

(defn make-arrow-quad
  [start-pos end-pos ctl-pt prop style]
  (let [pts (comp-quad-arrow-pts start-pos end-pos ctl-pt prop)]
    (poly-line pts (update style :svg dissoc :strokeDasharray))))

(defn- draw-arc-curve
  [{:keys [center radius]} style l t border-width pts]
  (let [c (pt-lp center l t border-width)]
    [:circle (merge style
                    {:cx (c 0)
                     :cy (c 1)
                     :r radius})]))

(rum/defc arc < rum/static
  [center radius start-angle stop-angle style]
  (let [bbox (arc-bbox center radius)]
    (render-curve draw-arc-curve (assoc style :params {:center center :radius radius}) (bbox 0) (bbox 1))))

(defn make-arrow-arc
  [s e prop style]
  (let [pts (comp-arc-arrow-pts s e prop)]
    (poly-line pts (update style :svg dissoc :strokeDasharray))))
