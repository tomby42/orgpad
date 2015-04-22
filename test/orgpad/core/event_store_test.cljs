(ns orgpad.core.event-store-test
  (:require [cljs.test :refer-macros [deftest testing is] ]
            [cljs.test.check :as tc]
            [cljs.test.check.generators :as gen]
            [cljs.test.check.properties :as prop :refer-macros [for-all]]
            [cljs.test.check.cljs-test :refer-macros [defspec]]
            [orgpad.core.event-store :as es]
            [wagjo.diff.core :as sdiff]))

;; (def gen-cmd (gen/elements [:conj :disj :assoc :dissoc]))
;; (def gen-idx (gen/not-empty (gen/vector (gen/one-of [gen/int gen/keyword]) 1 10)))
;; (def gen-event (gen/tuple gen-cmd gen-idx gen/int))
;; (def gen-events (gen/vector gen-event 1 100))

;; (defspec history-length-should-be-increasing ;; the name of the test
;;          100 ;; the number of iterations for test.check to test
;;          (let [es1 (es/new-event-store {})]
;;            (prop/for-all [e gen-events]
;;            (> (-> (es/update es1 e) :history count)
;;               (-> es1 :history count)))))

(defn get-rnd-kw
  [state]

  (-> state keys rand-nth))

(defn get-random-elem
  [state]

  (if (vector? state)     ;; vector branch
    (let [idx (-> state count rand-int)]
      [(get state idx) idx])
    (let [idx (get-rnd-kw state)]
      [(get state idx) idx])))

(defn pope
  [v]
  (if (empty? v)
    v
    (pop v)))

(defn gen-random-path
  [state pth]

  (if (and (coll? state) (not= (rand-int 3) 0))
    (let [[el idx] (get-random-elem state)]
      (if (and (coll? el) (empty? el))
        (conj pth idx)
        (gen-random-path el (conj pth idx))))
    (if (coll? state)
      pth
      (pope pth))))

(defn gen-random-col
 []
 (if (zero? (rand-int 2))
   []
   {}))

(defn gen-rnd-kw
  []

  (-> 100 rand-int str keyword))

(defn gen-random-cmd
  [{:keys [state] :as es} pth]

  (if (-> state (get-in pth) map?)
    (if (< (rand-int 5) 3)
      (es/assoces es pth (gen-rnd-kw) (gen-random-col))
      (if (empty? (get-in state pth))
        (es/assoces es pth (gen-rnd-kw) (gen-random-col))
        (es/dissoces es pth (get-rnd-kw (get-in state pth)))))
    (if (< (rand-int 5) 3)
      (es/conjes es pth (gen-random-col))
      (if (empty? (get-in state pth))
        (es/conjes es pth (gen-random-col))
        (es/popes es pth)))))

(defn gen-event-store
  [ev-store n]

  (loop [i 0
         es ev-store]
    (if (= i n)
      es
      (let [pth (gen-random-path (:state es) [])]
        (recur (inc i)
               (gen-random-cmd es pth))))))

(defn test-state-build
  [n m]

  (loop [i 0]
    (if (= i n)
      true
      (let [es (gen-event-store (es/new-event-store []) m)]
        (if (= (:state es) (es/state-from-to [] (:history es) 1 (-> es :history count)))
          (recur (inc i))
          (do
            (println "Wrong" es)
            false
          ))))))


(deftest event-store

  (testing "new"
    (let [e (es/new-event-store {})]
      (is (empty? (:state e)) "should be empty")
      (is (= (count (:history e)) 1) "should be one")))


  (testing "conjes"
    (let [e  (es/new-event-store [])
          e1 (es/conjes e [] [])
          e2 (es/conjes e1 [0] 0)]
      (is (= (-> e1 :state count) 1)
          "should be 1")
      (is (= (-> e1 :history last first) :conj)
          "should be conj")
      (is (= (-> e2 :state first count) 1)
          "should contain vector of length 1")
      (is (= (-> e2 :history last first) :conj)
          "should be conj too")))

  (testing "popes"
    (let [e (es/new-event-store [[1] 1])
          e1 (es/popes e [])
          e2 (es/popes e1 [0])]
      (is (= (-> e1 :state count) 1)
          "should contain one element")
      (is (= (-> e1 :history last first) :pop)
          "should be pop")
      (is (= (-> e2 :state first count) 0)
          "should be zero")
      (is (= (-> e2 :history last first) :pop)
          "should be pop too")))

  (testing "assoces"
    (let [e (es/new-event-store {})
          e1 (es/assoces e [] :a {})
          e2 (es/assoces e1 [:a] :b {})]
      (is (contains? (-> e1 :state) :a)
          "should contain :a")
      (is (= (-> e1 :history last first) :assoc)
          "should be assoc")
      (is (contains? (-> e2 :state :a) :b)
          "should contains :b")
      (is (= (-> e2 :history last first) :assoc)
          "should be assoc too")))

  (testing "dissoces"
    (let [e (es/new-event-store {:a {:b {}} :c 0})
          e1 (es/dissoces e [] :c)
          e2 (es/dissoces e1 [:a] :b)]
      (is (not (contains? (-> e1 :state) :c))
          "should not contain :c")
      (is (= (-> e1 :history last first) :dissoc)
          "should be dissoc")
      (is (not (contains? (-> e2 :state :a) :b))
          "should not contains :b")
      (is (= (-> e2 :history last first) :dissoc)
          "should be dissoc too")))

  (testing "patches"
    (let [e (es/new-event-store {:text "abcabba"})
          e1 (es/patches e [:text] [[[3 "b"] [7 "c"]] [0 1 5]])]
      (is (= (-> e1 :state :text) "cbabac")
          "should be equal")
      (is (= (-> e1 :history last first) :patch)
          "should be patch")))

  (testing "state from to"
    (is (test-state-build 100 10) "should equal"))
  )
