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
          _ (gc/create! global-cache 0 "default")]
      (is (= (gc/has-geocache? global-cache 0 "default") true)
          "should exist")
      (is (= (gc/has-geocache? global-cache 1 "default") false)
          "should not exist")

      ))

  (testing "clear"
    (let [global-cache #js {}
          _ (gc/create! global-cache 0 "default")
          _ (gc/create! global-cache 0 "foo")
          geocache (aget global-cache 0 "geocache" "default")
          geocache1 (aget global-cache 0 "geocache" "foo")
          _ (gc/add->place! geocache "0" 1)
          _ (gc/add->place! geocache "0" 2)
          _ (gc/add->place! geocache "0" 3)
          _ (gc/add->place! geocache "1" 1)
          _ (gc/add->place! geocache "2" 3)
          _ (gc/add->place! geocache "2" 2)

          _ (gc/add->place! geocache1 "0" 1)
          _ (gc/add->place! geocache1 "0" 2)
          _ (gc/add->place! geocache1 "0" 3)
          _ (gc/add->place! geocache1 "1" 1)
          _ (gc/add->place! geocache1 "2" 3)
          _ (gc/add->place! geocache1 "2" 2)
          _ (gc/clear! global-cache 0 #js [1 2])
          _ (gc/clear! global-cache 1 #js [1 2])]

      (is (= (jcolls/aget-nil geocache "0" 1) nil))
      (is (= (jcolls/aget-nil geocache "0" 2) nil))
      (is (= (jcolls/aget-nil geocache "0" 3) true))
      (is (= (jcolls/aget-nil geocache "1" 1) nil))
      (is (= (jcolls/aget-nil geocache "2" 2) nil))
      (is (= (jcolls/aget-nil geocache "2" 3) true))

      (is (= (jcolls/aget-nil geocache1 "0" 1) nil))
      (is (= (jcolls/aget-nil geocache1 "0" 2) nil))
      (is (= (jcolls/aget-nil geocache1 "0" 3) true))
      (is (= (jcolls/aget-nil geocache1 "1" 1) nil))
      (is (= (jcolls/aget-nil geocache1 "2" 2) nil))
      (is (= (jcolls/aget-nil geocache1 "2" 3) true))

      ))

  )
