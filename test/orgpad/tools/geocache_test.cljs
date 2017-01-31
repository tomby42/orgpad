(ns orgpad.tools.geocache-test
  (:require [cljs.test :as test :refer-macros [deftest testing is are]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop :include-macros true]
            [clojure.test.check.clojure-test :as tct :include-macros true]
            [orgpad.tools.geocache :as gc]
            [orgpad.tools.jcolls :as jcolls]))

(deftest geocache

  (testing "create"
    (let [global-cache #js {}
          _ (gc/create! global-cache 0)]
      (is (gc/has-geocache? global-cache 0)
          "should exist")
      (is (not (gc/has-geocache? global-cache 1))
          "should not exist")

      ))

  (testing "clear"
    (let [global-cache #js {}
          _ (gc/create! global-cache 0)
          geocache (aget global-cache 0 "geocache")
          _ (gc/add->place! geocache "0" 1)
          _ (gc/add->place! geocache "0" 2)
          _ (gc/add->place! geocache "0" 3)
          _ (gc/add->place! geocache "1" 1)
          _ (gc/add->place! geocache "2" 3)
          _ (gc/add->place! geocache "2" 2)
          _ (gc/clear! global-cache 0 #js [1 2])]
      (is (= (jcolls/aget-nil geocache "0" 1) nil))
      (is (= (jcolls/aget-nil geocache "0" 2) nil))
      (is (= (jcolls/aget-nil geocache "0" 3) true))
      (is (= (jcolls/aget-nil geocache "1" 1) nil))
      (is (= (jcolls/aget-nil geocache "2" 2) nil))
      (is (= (jcolls/aget-nil geocache "2" 3) true))))
  
  )
