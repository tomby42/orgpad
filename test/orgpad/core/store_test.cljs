(ns orgpad.core.store-test
  (:require [cljs.test :as test :refer-macros [deftest testing is are]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop :include-macros true]
            [clojure.test.check.clojure-test :as tct :include-macros true]
            [datascript.core :as d]
            [orgpad.core.store :as store]))

(defn- create-db
  []
  (d/empty-db {:friend {:db/cardinality :db.cardinality/many
                        :db/valueType :db.type/ref}}))

(def gen-store-items
  (gen/fmap (partial map-indexed (fn [idx x] (vector idx x)))
            (gen/not-empty (gen/vector gen/string))))

(def test-store-creation
  (prop/for-all
   [items gen-store-items]
   (let [db   (create-db)
         es1  (store/new-datom-store db)
         tx   (map (fn [[idx name]] {:db/id idx :name name}) items)
         es2  (store/transact es1 tx)]
     (pos? (count (.-history es2))))))

(tct/defspec store-test-new-store 100 test-store-creation)

(def test-undo-redo
  (prop/for-all
   [items gen-store-items
    n     (gen/choose 1 (inc (count items)))]
   (let [db   (create-db)
         es1  (store/new-datom-store db)
         tx   (map (fn [[idx name]] {:db/id idx :name name}) items)
         es2  (store/transact es1 tx)
         es3  (reduce (fn [es _] (store/undo es)) es2 (range n))
         es4  (reduce (fn [es _] (store/redo es)) es3 (range n))]
     (= (.-db es2) (.-db es4)))))

(tct/defspec store-test-undo-redo 100 test-undo-redo)

(deftest store-test

  (testing "new store"
    (let [db   (create-db)
          es1  (store/new-datom-store db)
          es2  (store/transact
                es1
                [{:db/id 1
                  :name "Bozko"
                  :friend 2
                  }
                 {:db/id 2
                  :name "Budko"
                  }
                 ])]
      (is (-> (.-history es2) count pos?)
          "should be non empty")

    ))

  (testing "undo"
    (let [db   (create-db)
          es1  (store/new-datom-store db)
          es2  (store/transact
                es1 [{:db/id 3
                      :name "Trubko"
                      :friend 2
                      }])
          es3  (store/undo es2)]

      (is (empty? (store/query es3 '[:find ?e :where [?e :name "Trubko"]]))
          "should be empty")

      ))

  (testing "redo"
    (let [db   (create-db)
          es1  (store/new-datom-store db)
          es2  (-> es1
                   (store/transact
                    [{:db/id 3
                      :name "Trubko"
                      :friend 2
                      }])
                   (store/transact
                    [{:db/id 3
                      :name "Trubko"
                      :friend 2
                      }]))
          es3   (-> es2
                    store/undo
                    store/redo)]

      (is (empty? (store/query (store/undo es2) '[:find ?e :where [?e :name "Trubko"]]))
          "should be empty")

      (is (not (empty? (store/query es3 '[:find ?e :where [?e :name "Trubko"]])))
          "should be present")

      ))

  (testing "changed?"
    (let [db   (create-db)
          es1  (store/new-datom-store db)
          es2  (->
                es1
                (store/transact
                 [{:db/id 1
                   :name "Bozko"
                   :friend 2
                   }
                  {:db/id 2
                   :name "Budko"
                   }
                  ])
                (store/reset-changes)
                (store/transact
                 [[:db/add 2 :friend 1]]))]

      (is (store/changed? es2 '[:find ?a ?v
                                :where [2 ?a ?v]])
          "should match changed")))

  )
