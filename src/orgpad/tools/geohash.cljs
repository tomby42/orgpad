(ns ^{:doc "Geohash  tools"}
  orgpad.tools.geohash
  (:require [cljsjs.latlon-geohash]))

(def ^:private MAX-COORD 1000000)
(def ^:private COORD-CENTER (/ MAX-COORD 2))
(def ^:private MAX-RESOLUTION 12)
(def ^:private BOX-RESOLUTION 7)

(def ^:private encode (aget js/Geohash "encode"))
(def ^:private decode (aget js/Geohash "decode"))
(def ^:private adjacent (aget js/Geohash "adjacent"))

(defn pos->hash
  "Map x, y coords to geohash with hash length res."
  [x y & [res]]
  (let [lon (/ (+ x COORD-CENTER) MAX-COORD)
        lat (/ (+ y COORD-CENTER) MAX-COORD)]
    (encode lat lon (or res MAX-RESOLUTION))))

(defn- lalo->px
  [l]
  (- (* l MAX-COORD) COORD-CENTER))

(defn hash->pos
  "Maps geohash to x, y in plane"
  [h]
  (let [c (decode h)]
    [(lalo->px (aget c "lon" )) (lalo->px (aget c "lat" ))]))

(defn- steps
  "Returns [dx dy] for horizontal and vertical step for given resolution"
  [res]
  (let [h (pos->hash 0 0 res)
        hc1 (hash->pos h)
        hc2 (hash->pos (adjacent h "e"))
        hc3 (hash->pos (adjacent h "n"))]
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
        dx2 (/ dx 1.9)
        dy2 (/ dy 1.9)
        cp (hash->pos (pos->hash x y res'))]
    ;; (js/console.log "bhi" x y w h x' y' dx dy cp)
    (loop [yc (cp 1)
           hs []]
      ;; (js/console.log "bhl" yc dy2 y' cp)
      (if (> yc (+ dy2 y'))
        hs
        (recur (+ yc dy)
               (loop [xc (cp 0)
                      hs' hs]
                 (if (> xc (+ dx2 x'))
                   hs'
                   (recur (+ xc dx)
                          (conj hs' (pos->hash xc yc res'))))))))))
