(ns orgpad.core.store-test
  (:require [cljs.test :as test :refer-macros [deftest testing is are]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop :include-macros true]
            [clojure.test.check.clojure-test :as tct :include-macros true]
            [datascript :as d]
            [orgpad.core.store :as store]))

(defn- create-connection
  []
  (d/create-conn {:friend {:db/cardinality :db.cardinality/many
                           :db/valueType :db.type/ref}}))


(def gen-store-items
  (gen/fmap (partial map-indexed (fn [idx x] (vector idx x)))
            (gen/not-empty (gen/vector gen/string))))

(def test-store-creation
  (prop/for-all
   [items gen-store-items]
   (let [conn (create-connection)
         es1  (store/new-store conn)
         tx   (map (fn [[idx name]] {:db/id idx :name name}) items)]
     (d/transact! conn tx)
     (pos? (-> es1 :history deref count)))))

(tct/defspec store-test-new-store 100 test-store-creation)

(def test-undo-redo
  (prop/for-all
   [items gen-store-items
    n     (gen/choose 1 (inc (count items)))]
   (let [conn (create-connection)
         es1  (store/new-store conn)
         tx   (map (fn [[idx name]] {:db/id idx :name name}) items)]
     (d/transact! conn tx)
     (let [db @conn]
       (dotimes [x n]
         (store/undo! es1))
       (dotimes [x n]
         (store/redo! es1))
       #_(println "1:" db)
       #_(println "2" @conn)
       (= db @conn)))))

(tct/defspec store-test-undo-redo 100 test-undo-redo)

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

  (testing "redo"
    (let [conn (create-connection)
          es1  (store/new-store conn)]
      (d/transact! conn [{:db/id 3
                          :name "Trubko"
                          :friend 2
                          }])

      (d/transact! conn [{:db/id 3
                          :name "Trubko"
                          :friend 2
                          }])


      (-> es1
        store/undo!
        store/redo!)

      (.log js/console (-> es1 :history deref))

      (is (not-empty (d/q '[:find ?e :where [?e :name "Trubko"]] @conn))
          "should be present")

      ))
  )
