(ns orgpad.components.graphics.utils
  (:require-macros [orgpad.tools.colls :refer [>-]])
  (:require [orgpad.tools.geom :refer [++ -- *c normalize] :as geom]
            [orgpad.tools.bezier :as bez]
            [orgpad.tools.colls :as colls]))

(defn comp-border-width
  [style]
  (+ 1
   (or (-> style :canvas :lineWidth) (-> style :canvas :line-width)
       (-> style :svg :strokeWidth) (-> style :svg :stroke-width) 0)))

(defn comp-bb
  [border-width pts]
  (let [bb (apply geom/points-bbox pts)
        shift [border-width border-width]]
    [(-- (bb 0) shift) (++ (bb 1) shift)]))

(defn dims
  [border-width pts]
  (let [bb (comp-bb border-width pts)]
    [(inc (- (>- bb 1 0) (>- bb 0 0)))
     (inc (- (>- bb 1 1) (>- bb 0 1)))]))

(defn left-top-corner
  [pts]
  [(apply min (map colls/vfirst pts)) (apply min (map colls/vsecond pts))])

(defn lp
  "Compute local point coordinate"
  [p c idx border-width]
  (+ (- (nth p idx) c) border-width))

(defn pt-lp
  [p l t border-width]
  [(lp p l 0 border-width) (lp p t 1 border-width)])

(defn arc-bbox
  [center radius]
  (let [d [radius radius]]
    [(geom/-- center d)
     (geom/++ center d)]))

(defn comp-quad-arrow-pts
  [start-pos end-pos ctl-pt prop]
  (let [arrow-pos (* (:orgpad/link-arrow-pos prop) 0.01)
        p1 (bez/get-point-on-quadratic-bezier start-pos ctl-pt end-pos arrow-pos)
        dir (-> p1 (-- (bez/get-point-on-quadratic-bezier start-pos ctl-pt end-pos (- arrow-pos 0.01))) normalize)
        ptmp (++ p1 (*c dir -15))
        n (geom/normal dir)
        p2 (++ ptmp (*c n 8))
        p3 (++ ptmp (*c (-- n) 8))]
    [p2 p1 p3]))

(defn comp-arc-arrow-pts
  [s e prop]
  (let [dir (normalize (-- s e))
        n (geom/normal dir)
        s' (++ (*c n -10) s)
        p1 (++ (*c dir 8) s')
        p2 (++ (*c dir -8) s')]
    [p1 s p2]))
