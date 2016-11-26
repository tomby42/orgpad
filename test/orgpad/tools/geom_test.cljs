(ns orgpad.tools.geom-test
  (:require [cljs.test :as test :refer-macros [deftest testing is are]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop :include-macros true]
            [clojure.test.check.clojure-test :as tct :include-macros true]
            [orgpad.tools.geom :as geom]))

(deftest vec-op

  (testing "add"
    (is (= [1 1] (geom/++ [1 0] [0 1]))
        "should equal")
    (is (= [0 0] (geom/++ [1 1] [-1 -1] [0 0]))
        "should equal"))

  (testing "sub"
    (is (= [1 -1] (geom/-- [1 0] [0 1]))
        "should equal")
    (is (= [0 0] (geom/-- [1 1] [1 1] [0 0]))
        "should equal"))

  (testing "times const"
    (is (= [0.5 0.5] (geom/*c [1 1] 0.5))
        "should equal"))

  (testing "dot product"
    (is (= 2 (geom/dot [1 1] [1 1]))
        "should equal")
    (is (= 0 (geom/dot [1 0] [0 1]))
        "should equal"))

  (testing "normal"
    (is (= [0 1] (geom/normal [1 0]))
        "should equal"))
  
  )
