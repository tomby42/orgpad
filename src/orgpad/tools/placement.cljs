(ns ^{:doc "Placement of units tools"}
  orgpad.tools.placement
  (:require [orgpad.tools.geocache :as geocache]
            [orgpad.tools.geohash :as geohash]))

(defn build-placement-info
  [bbs]
  (->> bbs
       (mapcat (fn [[[x y] [w h]]] (geohash/box->hashes x y w h 8)))
       set))

(defn place
  [placement-info init-pos size strategy]
  (loop [i 0
         pos (strategy init-pos init-pos size 0)]
    (let [h (geohash/box->hashes (pos 0) (pos 1) (size 0) (size 1))
          i+1 (inc i)]
      (if (contains? placement-info h)
        (recur i+1 (strategy init-pos pos size i+1))
        pos))))

(defn cross-strategy
  [init-pos pos size i]
  (let [dir (mod i 4)
        mult (-> i (/ 4) int inc)]
    (case dir
      0 [(+ (init-pos 0) (* mult (size 0))) (init-pos 1)]
      1 [(init-pos 1) (- (init-pos 1) (* mult (size 1)))]
      2 [(- (init-pos 0) (* mult (size 0))) (init-pos 1)]
      3 [(init-pos 1) (+ (init-pos 1) (* mult (size 1)))])))

(defn row-strategy
  [init-pos pos size i]
  [(+ (init-pos 0) (* (inc i) (size 0))) (init-pos 1)])

(defn column-strategy
  [init-pos pos size i]
  [(init-pos 0) (+ (init-pos 1) (* (inc i) (size 1)))])
