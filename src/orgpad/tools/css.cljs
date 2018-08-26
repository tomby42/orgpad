(ns ^{:doc "CSS tools"}
  orgpad.tools.css)

(defn- rotate-str
  [angle]
  (str "rotate(" angle "deg)"))

(defn- ts
  [x y]
  (str "translate(" x "px, " y "px)"))

(defn- translate-str
  [p]
  (cond
    (vector? p) (ts (p 0) (p 1))
    (map? p) (ts (p :x) (p :y))
    (number? p) (ts p p)))

(defn- ss
  [sx sy]
  (str "scale(" sx ", " sy ")"))

(defn- scale-str
  [s]
  (cond
    (vector? s) (ss (s 0) (s 1))
    (map? s) (ss (s :sx) (s :sy))
    (number? s) (ss s s)))

(defn- style
  [s]
  { :WebkitTransform s
    :MozTransform s
    :OTransform s
    :msTransform s
    :transform s })

(defn translate
  [p]
  (style (translate-str p)))

(defn rotate
  [angle]
  (style (rotate-str angle)))

(defn scale
  [s]
  (style (scale-str s)))

(defn transform
  [t]
  (style (reduce (fn [s [k v]]
                   (str s " "
                        (case k
                          :rotate (rotate-str v)
                          :translate (translate-str v)
                          :scale (scale-str v))))
                 "" t)))

(defn hex-color->rgba
  [hex-color]
  (let [c (js/parseInt (str "0x" (.substring hex-color 1)) 16)]
    (if (= (.-length hex-color) 7)
      (str "rgb(" (-> c (bit-shift-right 16) (bit-and 255))
           "," (-> c (bit-shift-right 8) (bit-and 255))
           "," (-> c (bit-and 255)) ")")
      (str "rgba(" (-> c (bit-shift-right 24) (bit-and 255))
           "," (-> c (bit-shift-right 16) (bit-and 255))
           "," (-> c (bit-shift-right 8) (bit-and 255))
           "," (/ (-> c (bit-and 255)) 255)")"))))

(defn format-color
  [c]
  (if (= (.-length c) 9)
    (hex-color->rgba c)
    c))

