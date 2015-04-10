(ns orgpad.core.event-store-test
  (:require [cljs.test :refer-macros [deftest testing is] ]
            [cljs.test.check :as tc]
            [cljs.test.check.generators :as gen]
            [cljs.test.check.properties :as prop :refer-macros [for-all]]
            [cljs.test.check.cljs-test :refer-macros [defspec]]
            [orgpad.core.event-store :as es]))

(def gen-cmd (gen/elements [:conj :disj :assoc :dissoc]))
(def gen-idx (gen/not-empty (gen/vector (gen/one-of [gen/int gen/keyword]) 1 10)))
(def gen-event (gen/tuple gen-cmd gen-idx gen/int))
(def gen-events (gen/vector gen-event 1 100))

(defspec history-length-should-be-increasing ;; the name of the test
         100 ;; the number of iterations for test.check to test
         (let [es1 (es/new-event-store {})]
           (prop/for-all [e gen-events]
           (> (-> (es/update es1 e) :history count)
              (-> es1 :history count)))))

(deftest event-store

  (testing "new"
    (let [e (es/new-event-store {})]
      (is (empty? (:state e)) "should be empty")
      (is (= (count (:history e)) 1) "should be one"))))
