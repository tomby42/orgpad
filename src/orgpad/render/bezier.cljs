(ns ^{:doc "Bezier rendering utilities"} orgpad.render.bezier
  (:require [orgpad.render.graph]))

;drawing canvas ID
(def canvas "mainCanvas")

(defn midpoint [pt1 pt2]
  (vector (/ (+ (pt1 0)
                (pt2 0))
             2)
          (/ (+ (pt1 1)
                (pt2 1))
             2)))

(defn draw-cubic-bezier [pts] "Draws bezier curve defined by 4 control points."
  (let [ctx (.getContext (.getElementById js/document canvas) "2d")]
    (doto ctx 
      .beginPath
      (.moveTo (get-in pts [0 0]) 
               (get-in pts [0 1]))
      (.bezierCurveTo (get-in pts [1 0])
                      (get-in pts [1 1])
                      (get-in pts [2 0])
                      (get-in pts [2 1])
                      (get-in pts [3 0])
                      (get-in pts [3 1]))
      .stroke)))

(defn get-cpts-3pts [sharpness start mid end direction] "Calculate two control points for cubic bezier from 3 points and sharpness factor, which affects the distance between control points."
  (let [shift (map - 
                   (midpoint start mid) 
                   (midpoint mid end))
        ratio (/ 1
                 (+ 1
                    (/ (orgpad.render.graph/norm start mid)
                       (orgpad.render.graph/norm mid end))))]
    (into []
          (map -
               mid
               (for [x shift]
                 (* x direction ratio sharpness))))))

(defn- cpts-closed-curve [sharpness pts] "Get vector of all cpts approximating closed curve. Change sharpness to fine-tune the approximation."
  (let [n (count pts)]
    (if (>= n 4)
      (conj (cpts-closed-curve sharpness (pop pts))
            (vector (pts (- n 1))
                    (get-cpts-3pts sharpness 
                                   (peek pts) 
                                   (pts (- n 2)) 
                                   (pts (- n 3))
                                   1)
                    (get-cpts-3pts sharpness 
                                   (pts (- n 2)) 
                                   (pts (- n 3))
                                   (pts (- n 4))
                                   -1)
                    (pts (- n 3))))
      (vector))))

(defn- close-points [pts] "Prepends last two pts, appends first pts"
  (conj (into (vector (pts (- (count pts) 2)) (peek pts))
              pts)
        (first pts) 
        (-> pts rest first)))

(defn draw-closed-curve 
  ([sharpness pts]
   (draw-closed-curve (cpts-closed-curve sharpness (close-points pts))))
  ([pts]
   (do (draw-cubic-bezier (peek pts))
       (if (seq pts)
         (draw-closed-curve (pop pts))))))

;test Bezier - move to separate file
(defn test-pts-gen [] 
  (into [] 
        (for [x (range 0 5)]
          (vector (* (js/Math.random) 300) (* (js/Math.random) 150)))))
(defn bezierTest []
  (let [ctx (.getContext (.getElementById js/document canvas) "2d")
        test-pts (test-pts-gen)]
    (do
      (.fillRect ctx ((test-pts 0) 0) ((test-pts 0) 1) 5 5)
      (.fillRect ctx ((test-pts 1) 0) ((test-pts 1) 1) 5 5)
      (.fillRect ctx ((test-pts 2) 0) ((test-pts 2) 1) 5 5)
      (.fillRect ctx ((test-pts 3) 0) ((test-pts 3) 1) 5 5)
      (.fillRect ctx ((test-pts 4) 0) ((test-pts 4) 1) 5 5)
      ;      (for [x (range 1 (count test-pts))]
      ;        (.fillRect ctx ((test-pts x) 0) ((test-pts x) 1) 5 5))
      (draw-closed-curve 1 test-pts))))

