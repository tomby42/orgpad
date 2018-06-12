(ns ^{:doc "Math tools"}
  orgpad.tools.bezier
  (:require [orgpad.tools.geom :refer [++ *c] :as geom]
            [orgpad.tools.colls :as colls]
            ;; [bezier-js]
            [Bezier]
            ))

(def ^:private bezier-js (aget js/window "Bezier"))

;; B(t) = (1-t)^2 * P_0 + 2(1-t)t * P_1 + t^2 * P_2
(defn get-point-on-quadratic-bezier
  [p1 p2 p3 t]
  (let [t' (- 1 t)]
    (++
     (*c p1 (* t' t'))
     (*c p2 (* 2 t' t))
     (*c p3 (* t t)))))

(defn get-point-on-bezier
  [pts t]
  (let [n  (count pts)
        t' (- 1 t)]
    (loop [k 1
           q (transient pts)]
      (if (== k n)
        (get q 0)
        (recur (inc k)
               (let [n-k (- n k)]
                 (loop [i  0
                        q' q]
                   (if (== i n-k)
                     q'
                     (recur (inc i)
                            (assoc! q' i (++ (*c (get q' i) t')
                                             (*c (get q' (inc i)) t))))
                     ))))
        ))))

(defn nearest-point-on
  "returns {x: 2, y: 2, t: 1, d: 1.4142135623730951}"
  [p1 p2 p3 p]
  (let [curve (bezier-js. (apply array (colls/minto p1 p2 p3)))]
    (.project curve #js {:x (nth p 0) :y (nth p 1)})))

(defn ->pt
  [p]
  #js {:x (nth p 0) :y (nth p 1)})

(defn get-quadratic-curve-ctl-point
  [p1 p2 p3 t]
  (let [curve (.quadraticFromPoints bezier-js (->pt p1) (->pt p2) (->pt p3) t)]
    [(aget curve "points" 1 "x")
     (aget curve "points" 1 "y")]))

(defn get-point-on-quadratic-curve
  [p1 p2 p3 t-old t-new]
  (let [curve (.quadraticFromPoints bezier-js (->pt p1) (->pt p2) (->pt p3) t-old)
        pt (.get curve t-new)]
    [(.-x pt) (.-y pt)]))
