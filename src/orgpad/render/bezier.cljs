(ns ^{:doc "Bezier rendering utilities"} orgpad.render.bezier )

(def canvas "mainCanvas")
(def ctx (.getContext (.getElementById js/document canvas) "2d"))

(defn norm [pt1 pt2] "Euclidian norm"
  (js/Math.sqrt
    (+ (* (- (pt1 0) (pt2 0))
                  (- (pt1 0) (pt2 0)))
            (* (- (pt1 1) (pt2 1))
                       (- (pt1 1) (pt2 1))))))

(defn midpoint [pt1 pt2]
  (vector (/ (+ (pt1 0)
                (pt2 0))
             2)
          (/ (+ (pt1 1)
                (pt2 1))
             2)))

(defn draw-cubic-bezier [start cp0 cp1 end] "Draws bezier curve defined by 4 control points."
  (do (.beginPath ctx)
      (.moveTo ctx (start 0) (start 1))
      (.bezierCurveTo ctx (cp0 0) (cp0 1) (cp1 0) (cp1 1) (end 0) (end 1))
      (.stroke ctx)))

(defn get-cpts-3pts [sharpness start mid end] "Calculate two control points for cubic bezier from 3 points and sharpness factor, which affects the distance between control points."
  (let [shift (map - 
                   (midpoint start mid) 
                   (midpoint mid end))
        ratio (/ 1
                 (+ 1
                    (/ (norm start mid)
                       (norm mid end))))]
    (vector (map +
               mid
               (for [x shift]
                    (-> ratio (- 1) (* x sharpness))))
          (map -
               mid
               (for [x shift]
                 (* x -1 ratio sharpness))))))

(defn get-cpts-curve [sharpness pts] "Get vector of all cpts approximating closed curve. Change sharpness to fine-tune the approximation."
  (if (>= (count pts) 3)
      (let [cpts (get-cpts-3pts sharpness 
                                (peek pts) 
                                (-> pts pop peek) 
                                (-> pts pop pop peek))]
        (conj (get-cpts-curve sharpness (pop pts)) 
              (-> pts pop peek) (cpts 0) (cpts 1)))
      pts))
