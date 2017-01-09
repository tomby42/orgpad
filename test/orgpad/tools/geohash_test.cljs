(ns orgpad.tools.geohash-test
  (:require [cljs.test :as test :refer-macros [deftest testing is are]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop :include-macros true]
            [clojure.test.check.clojure-test :as tct :include-macros true]
            [orgpad.tools.geohash :as gh]))

(deftest geohash

  (testing "position to geohash"
    (is (= "s006g7h0dyg0" (gh/pos->hash 0 0))
        "should equal")
    (is (= "s006g7h0" (gh/pos->hash 0 0 8))
        "should equal")
    (is (= (gh/pos->hash 0 0 9) (gh/pos->hash -1 0 9))
        "should equal")
    (let [p (gh/hash->pos (gh/pos->hash 0 0 8))]
      (is (= (gh/pos->hash (p 0) (p 1) 8) (gh/pos->hash 0 0 8))
          "should equal"))

    (let [p (gh/hash->pos (gh/pos->hash 49.591064453125 135.4217529296875))]
      (is (= (gh/pos->hash (p 0) (p 1) 8) (gh/pos->hash 49.591064453125 135.4217529296875 8))
          "should equal"))
    )

  (testing "box->hashes"
    (is (= ["s006g7h0"] (gh/box->hashes 0 0 1 1))
        "should contains only one hash")

    (is (= ["s006g7h0" "s006g7h2"] (gh/box->hashes 0 0 380 1))
        "should contains two hashes")

    (is (= ["s006g7h0" "s006g7h2" "s006g7h1" "s006g7h3"] (gh/box->hashes 0 0 400 200))
        "should contains four hashes")

    )

  )
