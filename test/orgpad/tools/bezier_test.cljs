(ns orgpad.tools.bezier-test
  (:require [cljs.test :as test :refer-macros [deftest testing is are]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop :include-macros true]
            [clojure.test.check.clojure-test :as tct :include-macros true]
            [orgpad.tools.geom :as geom]
            [orgpad.tools.bezier :as bez]))

(def p1 [0 0])
(def p2 [1 0])
(def p3 [1 1])

(deftest get-point-on-quadratic-bezier

  (testing "midpoint"
    (is (= [0.75 0.25] (bez/get-point-on-quadratic-bezier p1 p2 p3 0.5))
        "should be equal")
    (is (= [1 1] (bez/get-point-on-quadratic-bezier [0 0] [1 1] [2 2] 0.5))
        "should be equal")
    )

  )
      
(deftest get-point-on-bezier

  (testing "general quadratic bezier equals specialized implementation"
    (is (= [0.75 0.25]
           (bez/get-point-on-quadratic-bezier p1 p2 p3 0.5)
           (bez/get-point-on-bezier [p1 p2 p3] 0.5))
        "should be equal")
    (is (= [1 1]
           (bez/get-point-on-quadratic-bezier [0 0] [1 1] [2 2] 0.5)
           (bez/get-point-on-bezier [[0 0] [1 1] [2 2]] 0.5))
        "should be equal")
    )
  
  )
