(ns ^{:doc "Geohash  tools"}
  orgpad.tools.geohash
  (:require [cljsjs.latlon-geohash]))

(def ^:private MAX-COORD 1000000)
(def ^:private COORD-CENTER (/ MAX-COORD 2))
(def ^:private MAX-RESOLUTION 12)
(def ^:private BOX-RESOLUTION 8)

(defn pos->hash
  "Map x, y coords to geohash with hash length res."
  [x y & [res]]
  (let [lon (/ (+ x COORD-CENTER) MAX-COORD)
        lat (/ (+ y COORD-CENTER) MAX-COORD)]
    (js/Geohash.encode lat lon (or res MAX-RESOLUTION))))

(defn- lalo->px
  [l]
  (- (* l MAX-COORD) COORD-CENTER))

(defn hash->pos
  "Maps geohash to x, y in plane"
  [h]
  (let [c (js/Geohash.decode h)]
    [(lalo->px (aget c "lon" )) (lalo->px (aget c "lat" ))]))

(defn- steps
  "Returns [dx dy] for horizontal and vertical step for given resolution"
  [res]
  (let [h (pos->hash 0 0 res)
        hc1 (hash->pos h)
        hc2 (hash->pos (js/Geohash.adjacent h "e"))
        hc3 (hash->pos (js/Geohash.adjacent h "n"))]
    [(- (hc2 0) (hc1 0))
     (- (hc3 1) (hc1 1))]))

(def ^:private steps'
  (memoize steps))

(defn box->hashes
  "Returns collection of hashes containing given box"
  [x y w h & [res]]
  (let [res' (or res BOX-RESOLUTION)
        x' (+ x w)
        y' (+ y h)
        [dx dy] (steps' res')
        dx2 (/ dx 2)
        dy2 (/ dy 2)
        cp (hash->pos (pos->hash x y res'))]
    (loop [yc (cp 1)
           hs []]
      (if (> yc (+ dy2 y'))
        hs
        (recur (+ yc dy)
               (loop [xc (cp 0)
                      hs' hs]
                 (if (> xc (+ dx2 x'))
                   hs'
                   (recur (+ xc dx)
                          (conj hs' (pos->hash xc yc res'))))))))))
