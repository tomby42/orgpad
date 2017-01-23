(ns orgpad.tools.geom
  (:require [orgpad.tools.colls :as colls])
  (:require-macros orgpad.tools.geom orgpad.tools.colls))

(defn screen->canvas
  [transform p]
  (let [tr (transform :translate)
        s (transform :scale)]
    [(orgpad.tools.geom/t tr s p 0) (orgpad.tools.geom/t tr s p 1)]))

(defn canvas->screen
  [transform p]
  (let [tr (transform :translate)
        s (transform :scale)]
    [(orgpad.tools.geom/tr tr s p 0) (orgpad.tools.geom/tr tr s p 1)]))

(defn ++
  ([p1 p2]
   [(orgpad.tools.geom/pl p1 p2 0) (orgpad.tools.geom/pl p1 p2 1)])

  ([p1 p2 p3]
   (++ (++ p1 p2) p3)))

(defn --
  ([p]
   [(- (p 0)) (- (p 1))])

  ([p1 p2]
   [(orgpad.tools.geom/ml p1 p2 0) (orgpad.tools.geom/ml p1 p2 1)])

  ([p1 p2 p3]
   (-- (-- p1 p2) p3)))

(defn *c
  [p c]
  [(* (p 0) c) (* (p 1) c)])

(defn insideBB
  [bb p]
  (and (orgpad.tools.geom/insideInterval (orgpad.tools.colls/>- bb 0 0) (orgpad.tools.colls/>- bb 1 0) (p 0))
       (orgpad.tools.geom/insideInterval (orgpad.tools.colls/>- bb 0 1) (orgpad.tools.colls/>- bb 1 1) (p 1))))

(defn dot
  [p1 p2]
  (+ (* (nth p1 0) (nth p2 0)) (* (nth p1 1) (nth p2 1))))

(defn vsize
  [v]
  (js/Math.sqrt (dot v v)))

(defn normal
  [dir]
  (let [size (vsize dir)]
    [(-> dir (nth 1) (/ size) -) (/ (nth dir 0) size)]))

(defn flipx
  [v]
  [(- (v 0)) (v 1)])

(defn flipy
  [v]
  [(v 0) (- (v 1))])

(defn normalize
  [v]
  (*c v (/ 1 (vsize v))))

(defn distance
  [p1 p2]
  (vsize (-- p1 p2)))

(defn points-bbox
  [& points]
  (let [xs (map colls/vfirst points)
        ys (map colls/vsecond points)]
    [[(apply min xs) (apply min ys)]
     [(apply max xs) (apply max ys)]]))

(defn link-middle-point
  [start-pt end-pt mid-pt-rel]
  (++ (*c (++ start-pt end-pt) 0.5) mid-pt-rel))

(defn link-middle-ctl-point
  [start-pt end-pt mid-pt]
  (*c (-- mid-pt (*c start-pt 0.25) (*c end-pt 0.25)) 2))

(defn link-arc-center
  [start-pt mid-pt]
  (*c (++ start-pt mid-pt) 0.5))

(defn link-arc-radius
  [start-pt mid-pt]
  (/ (distance start-pt mid-pt) 2))

(defn link-bbox
  [start-pt end-pt mid-pt-rel]
  (let [mid-pt (link-middle-point start-pt end-pt mid-pt-rel)]
    (if (= start-pt end-pt)
      (let [c (link-arc-center start-pt mid-pt)
            r (link-arc-radius start-pt mid-pt)
            r-shift [r r]]
        [(-- c r-shift) (++ c r-shift)])
      (points-bbox start-pt end-pt (link-middle-ctl-point start-pt end-pt mid-pt)))))
