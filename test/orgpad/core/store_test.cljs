(ns orgpad.core.store-test
  (:require [cljs.test :as test :refer-macros [deftest testing is]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop :include-macros true]
            [datascript :as d]
            [orgpad.core.store :as store]
            [wagjo.diff.core :as sdiff]))


(defn add-states
  [state-history from to]
  (reduce (fn [state-history num]
            (store/add-state state-history num num))
          state-history (range from to)))


(defn nei-diff-eq-step?
  [row step]

  (let [r   (mapv first row)
        n   (count r)]
    (->> (map - (subvec r 1) (subvec r 0 (dec n)))
         (every? (partial = step)))))

(defn state-history-test-run
  [step n-runs]

  (let [shistory      (store/new-state-history step)
        shistory1     (add-states shistory 0 n-runs)]
    (and (= (-> shistory1 :states count) (Math/round (/ (Math/log n-runs) (Math/log step))))
         (first (reduce (fn [[state mult] row]
                          (if state
                            [(nei-diff-eq-step? row mult) (* mult step)]
                            [false 0])) [true 1] (-> shistory1 :states)))
         )))



(deftest event-store-test

  (testing "state history"
    (let [shistory      (store/new-state-history 4)
          shistory1     (add-states shistory 0 4)
          shistory2     (add-states shistory1 4 16)
          shistory3     (add-states shistory2 16 64)]
      (is (nei-diff-eq-step? (get-in shistory1 [:states 0]) 1)
          "should has step 1")
      ))

  (testing "state history 1"
    (is (state-history-test-run 4 16)
        "should be true")
    )

  (testing "new event store"
    (let [conn (d/create-conn {:friend {:db/cardinality :db.cardinality/many
                                        :db/valueType :db.type/ref}})
          es1  (store/new-store conn 16)]
      (d/transact! conn [{:db/id 1
                          :name "Bozko"
                          :friend 2
                          }
                         {:db/id 2
                          :name "Budko"
                          }
                         ])
      (.log js/console (-> es1 :history deref))
      (is (not= (-> es1 :history deref count) 0)
          "should be non empty")
    ))

  )
