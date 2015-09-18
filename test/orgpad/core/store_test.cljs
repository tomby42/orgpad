(ns orgpad.core.store-test
  (:require [cljs.test :as test :refer-macros [deftest testing is are]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop :include-macros true]
            [datascript :as d]
            [orgpad.core.store :as store]))


(defn- create-connection
  []
  (d/create-conn {:friend {:db/cardinality :db.cardinality/many
                           :db/valueType :db.type/ref}}))

(deftest store-test

  (testing "new store"
    (let [conn (create-connection)
          es1  (store/new-store conn)]
      (d/transact! conn [{:db/id 1
                          :name "Bozko"
                          :friend 2
                          }
                         {:db/id 2
                          :name "Budko"
                          }
                         ])
      #_(.log js/console (-> es1 :history deref))
      (is (not= (-> es1 :history deref count) 0)
          "should be non empty")

    ))

  (testing "undo"
    (let [conn (create-connection)
          es1  (store/new-store conn)]
      (d/transact! conn [{:db/id 3
                          :name "Trubko"
                          :friend 2
                          }])

      (store/undo! es1)

      (.log js/console (-> es1 :history deref))

      (is (empty (d/q '[:find ?e :where [?e :name "Trubko"]] @conn))
          "should be empty")

      ))

  )
