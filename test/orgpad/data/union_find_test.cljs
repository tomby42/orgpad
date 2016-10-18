(ns orgpad.data.union-find-test
  (:require [cljs.test :as test :refer-macros [deftest testing is are]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop :include-macros true]
            [clojure.test.check.clojure-test :as tct :include-macros true]
            [orgpad.data.union-find :as uf]))

(deftest test-union-find
  (let [set (-> (uf/union-find 1 2 3 4 5 6)
                (uf/union 1 2)
                (uf/union 3 4)
                (uf/union 4 5))]
    (testing "Missing elements have nil leaders."
      (is (= [set nil] (uf/get-canonical set 10))))
    (testing "Singleton sets are their own leaders."
      (is (= 6 (second (uf/get-canonical set 6)))))
    (testing "Singleton sets unioned with themselves are still their own leaders."
      (is (= 6 (second (uf/get-canonical (uf/union set 6 6) 6)))))
    (testing "Unioning from both sides of size works as expected"
      (let [set (uf/union set 1 3)
            set-left  (uf/union set 1 4)
            set-right (uf/union set 4 1)]
        (is (= 1 (set-left  1)))
        (is (= 1 (set-right 1)))))
    (testing "Connected singletons have the same leader."
      (let [[set a] (uf/get-canonical set 1)
            [set b] (uf/get-canonical set 2)
            [set c] (uf/get-canonical set 3)
            [set d] (uf/get-canonical set 4)
            [set e] (uf/get-canonical set 5)]
        (is (= a b))
        (is (= c d))
        (is (= c e))
        (is (not= b c))
        (let [set (uf/union set 2 3)
              [set a] (uf/get-canonical set 1)
              [set c] (uf/get-canonical set 3)]
          (is (= a c 1)))))
    (testing "Seq returns only leader elements"
      (is (= 3 (count (seq set))))
      (is (= #{1 3 6} (into #{} (seq set)))))
    (testing "Count counts the number of connected components."
      (is (= 3 (count set)))
      (is (= 2 (count (uf/union set 1 3)))))
    (testing "Conj adds new singletons"
      (let [set (conj set 7)]
        (is (= 4 (count set)))
        (is (= 3 (count (uf/union set 6 7))))
        (is (= 7 (set 7)))
        (is (= 6 ((uf/union set 6 7) 7)))))

    (testing "union-find is gettable"
      (is (= 1 (get set 2)))
      (is (= 1 (get set 1)))
      (is (= nil (get set 10)))
      (is (= :not-found (get set 10 :not-found))))

    (testing "union-find is a function"
      (is (= 1 (set 2)))
      (is (= 1 (set 1)))
      (is (= nil (set 10)))
      (is (= :not-found (set 10 :not-found))))

    (testing "supports meta"
      (is (= {:with :meta} (meta (with-meta set {:with :meta})))))

    (testing "equality works right"
      (is (= set set))
      (is (not= set (conj set 8)))
      (is (= (uf/union set 5 6) (uf/union set 6 5))))

    (testing "unioning a missing element is a no-op."
      (is (= set (uf/union set 5 10))))))

(deftest test-transient-union-find
  (let [master-set (transient (-> (uf/union-find 1 2 3 4 5 6)
                                  (uf/union 1 2)
                                  (uf/union 3 4)
                                  (uf/union 4 5)))]


    (testing "Missing elements have nil leaders."
      (let [set (transient (uf/union-find 1 2 3))]
        (is (= [set nil] (uf/get-canonical set 10)))))
    (testing "Singleton sets are their own leaders."
      (let [set (transient (uf/union-find 1 2 3 6))]
       (is (= 6 (second (uf/get-canonical set 6))))))
    (testing "Singleton sets unioned with themselves are still their own leaders."
      (let [set (transient (uf/union-find 1 2 3 6))]
       (is (= 6 (second (uf/get-canonical (uf/union! set 6 6) 6)))) )
      )
    (testing "Unioning from both sides of size works as expected"
      (let [set (transient (uf/union-find 1 2 3 4))
            set (uf/union! set 1 3)
            set-left  (uf/union! set 1 4)
            set-right (uf/union! set 4 1)]
        (is (= 1 (set-left  1)))
        (is (= 1 (set-right 1)))))
    (testing "Connected singletons have the same leader."
      (let [set master-set
            [set a] (uf/get-canonical set 1)
            [set b] (uf/get-canonical set 2)
            [set c] (uf/get-canonical set 3)
            [set d] (uf/get-canonical set 4)
            [set e] (uf/get-canonical set 5)]
        (is (= a b))
        (is (= c d))
        (is (= c e))
        (is (not= b c))
        (let [set (uf/union! set 2 3)
              [set a] (uf/get-canonical set 1)
              [set c] (uf/get-canonical set 3)]
          (is (= a c 1)))))
    (testing "Count counts the number of connected components."
      (let [set (transient (-> (uf/union-find 1 2 3 4 5 6)
                (uf/union 1 2)
                (uf/union 3 4)
                (uf/union 4 5)))]
        (is (= 3 (count set)))
        (is (= 2 (count (uf/union! set 1 3))))
        ))
    (testing "Conj adds new singletons"
      (let [set (transient (-> (uf/union-find 1 2 3 4 5 6)
                (uf/union 1 2)
                (uf/union 3 4)
                (uf/union 4 5)))
            set (conj! set 7)
            set (conj! set 8)]
        (is (= 5 (count set)))
        (is (= 4 (count (uf/union! set 6 7))))
        (is (= 8 (set 8)))
        (is (= 6 ((uf/union! set 6 7) 7)))))

    (testing "union-find is gettable"
      (is (= 1 (get master-set 2)))
      (is (= 1 (get master-set 1)))
      (is (= nil (get master-set 10)))
      (is (= :not-found (get master-set 10 :not-found))))

    (testing "union-find is a function"
      (is (= 1 (master-set 2)))
      (is (= 1 (master-set 1)))
      (is (= nil (master-set 10)))
      (is (= :not-found (master-set 10 :not-found))))

    (testing "equality works right"
      (let [set-1 (transient (-> (uf/union-find 1 2 3 4 5 6)
                (uf/union 1 2)
                (uf/union 3 4)
                (uf/union 4 5)))
            set-2 (transient (-> (uf/union-find 1 2 3 4 5 6)
                (uf/union 1 2)
                (uf/union 3 4)
                (uf/union 4 5)))]
        (is (not= set-1 set-2))))
    (testing "unioning a missing element is a no-op."
      (is (= master-set (uf/union! master-set 5 10))))
    )
  )
