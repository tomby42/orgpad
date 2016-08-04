(ns orgpad.tools.geom)

(defmacro ^:private t
  [translate scale p idx]
  `(+ (~translate ~idx) (* (~p ~idx) ~scale)))

(defmacro ^:private pl
  [p1 p2 idx]
  `(+ (~p1 ~idx) (~p2 ~idx)))

(defmacro insideInterval
  [l r p]
  `(and (<= ~l ~p)
        (<= ~p ~r)))
