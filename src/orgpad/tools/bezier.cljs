(ns ^{:doc "Math tools"}
  orgpad.tools.bezier
  (:require [orgpad.tools.geom :refer [++ *c] :as geom]))

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
