(ns orgpad.tools.order-numbers-test
  (:require [cljs.test :as test :refer-macros [deftest testing is are]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop :include-macros true]
            [clojure.test.check.clojure-test :as tct :include-macros true]
            [orgpad.tools.order-numbers :as on]))

(deftest order-numbers

  (testing "zeros"
    (is (= (on/zeros 1) on/first-cipher)
        "should be equal")
    (is (= (on/zeros 0) "")
        "should be empty string")
    (is (= (on/zeros 5) (str on/first-cipher on/first-cipher on/first-cipher on/first-cipher on/first-cipher))
        (str "should return five " on/first-cipher))
    )

  (testing "incrementing"
    (let [num1 (on/zeros 2)
          num2 (str on/first-cipher on/last-cipher on/last-cipher)
          ]
      (is (= (on/inc-on-pos num1 1) (str on/first-cipher on/second-cipher))
          "should be equal")

      (is (= (on/inc-on-pos num2 2) (str on/second-cipher on/first-cipher on/first-cipher))
          "should propagate change")

      ))

  (testing "number between"
    (let [num1 (on/zeros 3)
          num2 (str on/first-cipher on/second-cipher)
          num3 (on/number-between num1 num2)
          num4 (str on/first-cipher on/last-cipher)
          num5 (on/number-between num1 num4)]
      (is (= num3 (str (on/zeros 2) on/mid-cipher))
          "should equal")
      (is (< num1 num3 num2)
          "should be ascending")
      (is (< num1 num5 num4)
          "should be in order")
      )
    
    )
  
  )
