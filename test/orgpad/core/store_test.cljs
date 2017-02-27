(ns orgpad.core.store-test
  (:require [cljs.test :as test :refer-macros [deftest testing is are]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop :include-macros true]
            [clojure.test.check.clojure-test :as tct :include-macros true]
            [cljs.reader :as reader]
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
     (pos? (count (.-history (.-history-records es2)))))))

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
      (is (-> (.-history (.-history-records es2)) count pos?)
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


  (testing "undo with rewrite"
    (let [db   (create-db)
          es1  (store/new-datom-store db)
          es2  (store/transact es1
                    [{:db/id 3
                      :name "Trubko"
                      :friend 2
                      }])
          es3  (store/transact es2
                    [[:db/add 3 :name "Trabko"]])]

      (is (not (empty? (store/query es3 '[:find ?e :where [?e :name "Trabko"]])))
          "should be present")

      (is (empty? (store/query (store/undo es3) '[:find ?e :where [?e :name "Trabko"]]))
          "should be empty")

      (is (not (empty? (store/query (store/undo es3) '[:find ?e :where [?e :name "Trubko"]])))
          "should be present")

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
                    [{:db/id 2
                      :name "Budko"
                      :friend 3
                      }]))
          es3   (-> es2
                    store/undo
                    store/redo)]

      (is (empty? (store/query (store/undo es2) '[:find ?e :where [?e :name "Budko"]]))
          "should be empty")

      (is (not (empty? (store/query es3 '[:find ?e :where [?e :name "Budko"]])))
          "should be present")

      ))

   (testing "undo finger"
     (let [db   (create-db)
           es1  (store/new-datom-store db)
           es2  (-> es1
                    (store/transact
                     [{:db/id 3
                       :name "Trubko"
                       :friend 2
                       }])
                    (store/transact
                     [{:db/id 2
                       :name "Budko"
                       :friend 3
                       }]))
           es3   (store/undo es2)
           es4   (store/transact es3
                                 [{:db/id 4
                                   :name "Fero"
                                   :friend 3
                                   }])
           es5   (store/undo es4)
           ]
       (is (empty? (store/query es5 '[:find ?e :where [?e :name "Budko"]]))
           "Budko should not be in db")
       (is (empty? (store/query es5 '[:find ?e :where [?e :name "Fero"]]))
           "Fero should not be in db")
       (is (not (empty? (store/query es5 '[:find ?e :where [?e :name "Trubko"]])))
           "Trubko should be in db")
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

  (testing "serialization of DatomStore"
    (let [es1  (store/new-datom-store (create-db))]
      (is (= (pr-str es1) (str "#orgpad/DatomStore {:db "
                               "#datascript/DB {"
                               ":schema {:friend {:db/cardinality :db.cardinality/many, "
                               ":db/valueType :db.type/ref}}, "
                               ":datoms ["
                               "]}"
                               "}"))
          "should serialize empty db")
      (is (= (cljs.reader/read-string (pr-str es1)) es1)
          "should read empty db"))

    (let [es1  (-> (store/new-datom-store (create-db))
                   (store/transact
                    [{:db/id 1
                      :name "Bozko"
                      :friend 2
                      }
                     {:db/id 2
                      :name "Budko"
                      }
                     ]))]
      (is (= (pr-str es1) (str "#orgpad/DatomStore {:db "
                               "#datascript/DB {"
                               ":schema {:friend {:db/cardinality :db.cardinality/many, "
                               ":db/valueType :db.type/ref}}, "
                               ":datoms ["
                               "[1 :friend 2 536870913] "
                               "[1 :name \"Bozko\" 536870913] "
                               "[2 :name \"Budko\" 536870913]"
                               "]}"
                               "}"))
          "should serialize non empty db")
      (is (= (.-db (cljs.reader/read-string (pr-str es1))) (.-db es1))
          "should read non empty db"))
    )

  (testing "serialization of DatomAtomStore"
    (let [es1  (store/new-datom-atom-store {} (create-db))]
      (is (= (pr-str es1) (str "#orgpad/DatomAtomStore {"
                               ":datom "
                               "#orgpad/DatomStore {:db "
                               "#datascript/DB {"
                               ":schema {:friend {:db/cardinality :db.cardinality/many, "
                               ":db/valueType :db.type/ref}}, "
                               ":datoms ["
                               "]}"
                               "}"
                               " :atom "
                               "{}"
                               "}"
                               ))
          "should serialize empty db")
      (is (= (cljs.reader/read-string (pr-str es1)) es1)
          "should read empty db"))

    (let [es1  (-> (store/new-datom-atom-store {:a 1} (create-db))
                   (store/transact
                    [{:db/id 1
                      :name "Bozko"
                      :friend 2
                      }
                     {:db/id 2
                      :name "Budko"
                      }
                     ]))]
      (is (= (pr-str es1) (str "#orgpad/DatomAtomStore {"
                               ":datom "
                               "#orgpad/DatomStore {:db "
                               "#datascript/DB {"
                               ":schema {:friend {:db/cardinality :db.cardinality/many, "
                               ":db/valueType :db.type/ref}}, "
                               ":datoms ["
                               "[1 :friend 2 536870913] "
                               "[1 :name \"Bozko\" 536870913] "
                               "[2 :name \"Budko\" 536870913]"
                               "]}"
                               "}"
                               " :atom "
                               "{:a 1}"
                               "}"
                               ))
          "should serialize non empty db")
      (is (= (-> (cljs.reader/read-string (pr-str es1)) .-datom .-db) (-> es1 .-datom .-db))
             "should read non empty db"))
    )

  )
