(ns ^{:doc "Collection tools"}
  orgpad.tools.colls
  (:require-macros orgpad.tools.colls))

(defn minto
  [f & r]
  (reduce into f r))

(defn shallow-eq
  [s1 s2]
  (let [n (count s1)]
    (if (== n (count s2))
      (loop [ss1 s1
             ss2 s2
             s   true]
        (if s
          (if (empty? ss1)
            true
            (recur (rest ss1) (rest ss2) (identical? (first ss1) (first ss2))))
          false))
      false)))

(defn semi-shallow-eq
  [s1 s2 eq-fns]
  (let [n (count s1)]
    (if (== n (count s2))
      (loop [ss1 s1
             ss2 s2
             eqs eq-fns
             s   true]
        (if s
          (if (empty? ss1)
            true
            (recur (rest ss1) (rest ss2) (rest eqs) ((first eqs) (first ss1) (first ss2))))
          false))
      false)))

(defn vfirst
  [v]
  (nth v 0))

(defn vsecond
  [v]
  (nth v 1))
