(ns orgpad.tools.geom
  (:require-macros orgpad.tools.geom orgpad.tools.colls))
  
(defn screen->canvas
  [transform p]
  (let [tr (transform :translate)
        s (transform :scale)]
    [(orgpad.tools.geom/t tr s p 0) (orgpad.tools.geom/t tr s p 1)]))

(defn canvas->screen
  [transform p]
  (let [tr (transform :translate)
        s (transform :scale)]
    [(orgpad.tools.geom/tr tr s p 0) (orgpad.tools.geom/tr tr s p 1)]))

(defn ++
  [p1 p2]
  [(orgpad.tools.geom/pl p1 p2 0) (orgpad.tools.geom/pl p1 p2 1)])

(defn --
  ([p1 p2]
   [(orgpad.tools.geom/ml p1 p2 0) (orgpad.tools.geom/ml p1 p2 1)])

  ([p1 p2 p3]
   (-- (-- p1 p2) p3)))

(defn *c
  [p c]
  [(* (p 0) c) (* (p 1) c)])

(defn insideBB
  [bb p]
  (and (orgpad.tools.geom/insideInterval (orgpad.tools.colls/>- bb 0 0) (orgpad.tools.colls/>- bb 1 0) (p 0))
       (orgpad.tools.geom/insideInterval (orgpad.tools.colls/>- bb 0 1) (orgpad.tools.colls/>- bb 1 1) (p 1))))
