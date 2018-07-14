(ns orgpad.tools.orgpad-test
  (:require [cljs.test :as test :refer-macros [deftest testing is are]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop :include-macros true]
            [clojure.test.check.clojure-test :as tct :include-macros true]
            [orgpad.core.orgpad :as oc]
            [orgpad.core.store :as store]
            [orgpad.tools.orgpad :as ot]
            [orgpad.tools.colls :as colls]))

(deftest orgpad-tools

  (testing "children search by text pattern"
    (let [db (-> (oc/empty-orgpad-db)
                 (store/transact [{:db/id -1
                                   :orgpad/refs -3
                                   :orgpad/props-refs -2}
                                  {:db/id -2
                                   :orgpad/atom "foo baz"}
                                  {:db/id -3
                                   :orgpad/refs -5
                                   :orgpad/props-refs -4}
                                  {:db/id -4
                                   :orgpad/atom "haskel clojure"}
                                  {:db/id -5
                                   :orgpad/props-refs -6}
                                  {:db/id -6
                                   :orgpad/atom "math phys"}
                                  {:db/id -7
                                   :orgpad/props-refs -8}
                                  {:db/id -8
                                   :orgpad/atom "foo math"}
                                  [:db/add 0 :orgpad/refs -1]
                                  [:db/add 0 :orgpad/refs -7]]))
          res (ot/search-child-by-descendant-txt-pattern db 0 "math")]
      (is (= 2 (count res))
          "should find two children")
      ))

  (testing "uid <-> squuid remaping"
    (let [db (-> (oc/empty-orgpad-db)
                 (store/transact [{:db/id 4
                                   :orgpad/refs [7 8]
                                   :orgpad/refs-order (sorted-set-by colls/first-< ["1" 7] ["2" 8])
                                   :orgpad/props-refs [5 6]}
                                  {:db/id 5
                                   :orgpad/refs 4
                                   :orgpad/view-path [0 :default]}
                                  {:db/id 6
                                   :orgpad/refs 4
                                   :orgpad/context-unit 0}
                                  {:db/id 7
                                   :orgpad/type :orgpad/unit}
                                  {:db/id 8
                                   :orgpad/type :orgpad/unit}
                                  [:db/add 0 :orgpad/refs 4]]))
          datoms (-> db store/datom-atom-store-ds-db seq)
          squuid-datoms (ot/datoms-uid->squuid datoms)
          uid-datoms (ot/datoms-squuid->uid (:datoms squuid-datoms) 0 (-> squuid-datoms :mapping clojure.set/map-invert))]
      (is (= (into [] (:datoms uid-datoms)) (into [] datoms))
          "datoms seqs should be equal")))
  )

