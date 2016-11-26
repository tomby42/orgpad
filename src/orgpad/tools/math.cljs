(ns ^{:doc "Math tools"}
  orgpad.tools.math)

(def pi (.-PI js/Math))
(def cos (.-cos js/Math))
(def sin (.-sin js/Math))

(defn deg->rads
  [deg]
  (-> deg (* pi) (/ 180)))

(defn psum
  [seq from to]
  (reduce + (subvec seq from to)))
