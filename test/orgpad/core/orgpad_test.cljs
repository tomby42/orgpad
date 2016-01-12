(ns orgpad.core.orgpad-test
  (:require [cljs.test :as test :refer-macros [deftest testing is are]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop :include-macros true]
            [clojure.test.check.clojure-test :as tct :include-macros true]
            [datascript.core :as d]
            [orgpad.core.store :as store]
            [orgpad.core.orgpad :as orgpad]))

(deftest empty-orgpad-db

  (testing "root unit presence"
    (let [o (orgpad/empty-orgpad-db)]
      (-> (store/query o '[:find ?e :where [0 :orgpad/type :orgpad/root-unit]])
          empty? not)))

  )
