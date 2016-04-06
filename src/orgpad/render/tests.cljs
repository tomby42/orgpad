(ns orgpad.render.tests
  (:require [cljs.test :refer-macros [testing is deftest run-tests]]
            [orgpad.render.graph]
            [orgpad.render.metaball]))

(def test-nodes
  (list
    {:center [0 0] :size 1}
    {:center [10 0] :size 1}
    {:center [0 10] :size 1}
    {:center [10 5] :size 1}))

;graph

(deftest test-generate-edges
  (is (= (orgpad.render.graph/generate-edges test-nodes)
         (list
           [(nth test-nodes 0) (nth test-nodes 1)]
           [(nth test-nodes 0) (nth test-nodes 2)]
           [(nth test-nodes 0) (nth test-nodes 3)]
           [(nth test-nodes 1) (nth test-nodes 2)]
           [(nth test-nodes 1) (nth test-nodes 3)]
           [(nth test-nodes 2) (nth test-nodes 3)]))))

(deftest test-rate-edges
  (is (= (orgpad.render.graph/rate-edges test-nodes)
         [{:weight 5.0, :nodes [{:center [10 0], :size 1}
                                {:center [10 5], :size 1}]}
          {:weight 10.0, :nodes [{:center [0 0], :size 1}
                                 {:center [10 0], :size 1}]}
          {:weight 10.0, :nodes [{:center [0 0], :size 1}
                                 {:center [0 10], :size 1}]}
          {:weight 11.180339887498949, :nodes [{:center [0 0], :size 1}
                                               {:center [10 5], :size 1}]}
          {:weight 11.180339887498949, :nodes [{:center [0 10], :size 1}
                                               {:center [10 5], :size 1}]}
          {:weight 14.142135623730951, :nodes [{:center [10 0], :size 1}
                                               {:center [0 10], :size 1}]}])))

(deftest test-create-basic-components
  (is (= (orgpad.render.graph/create-basic-components test-nodes)
         (vector
           #{{:center [0 0], :size 1}}
           #{{:center [10 0], :size 1}}
           #{{:center [0 10], :size 1}}
           #{{:center [10 5], :size 1}}))))

(deftest test-component-merge
  (testing "Testing merging " 
    (testing "distinct components"
      (is (= (let [comps (orgpad.render.graph/create-basic-components test-nodes)] ;merge distinct
               (orgpad.render.graph/component-merge comps
                                                    (first test-nodes)
                                                    (second test-nodes)))
             (list
               #{{:center [0 0], :size 1} {:center [10 0], :size 1}}
               #{{:center [0 10], :size 1}}
               #{{:center [10 5], :size 1}}))))
    (testing "same components"
      (is (= (let [comps (orgpad.render.graph/create-basic-components test-nodes)] ;merge same
               (orgpad.render.graph/component-merge comps
                                                    (first test-nodes)
                                                    (first test-nodes)))
             (list
               #{{:center [0 0], :size 1}}
               #{{:center [10 0], :size 1}}
               #{{:center [0 10], :size 1}}
               #{{:center [10 5], :size 1}}))))))

(deftest test-spanning-tree
  (is (= (orgpad.render.graph/spanning-tree test-nodes)
         (vector
           {:weight 10.0, :nodes [{:center [0 0], :size 1} {:center [0 10], :size 1}]}
           {:weight 10.0, :nodes [{:center [0 0], :size 1} {:center [10 0], :size 1}]}
           {:weight 5.0, :nodes [{:center [10 0], :size 1} {:center [10 5], :size 1}]}))))

;metaball
(def test-edge {:weight 25 :nodes [(nth test-nodes 1) (nth test-nodes 3)]})

(deftest test-blob-fn
  (testing "Testing blob-fn of "
    (testing "a node at its centre."
      (is (= (orgpad.render.metaball/blob-fn (nth test-nodes 0) [0 0])
             1)))
    (testing "2nd test-node at [0 0]"
      (is (= (orgpad.render.metaball/blob-fn (nth test-nodes 1) [0 0])
             0.09)))
    (testing "3rd test-node at [0 0]"
      (is (= (orgpad.render.metaball/blob-fn (nth test-nodes 2) [0 0])
             0.09)))
    (testing "4th test-node at [0 0]"
      (is (= (orgpad.render.metaball/blob-fn (nth test-nodes 3) [0 0])
             0.08049844718999243)))))

(deftest test-total-blob-fn
  (testing "Testing blob function of test-nodes."
    (is (= (orgpad.render.metaball/total-blob-fn test-nodes [0 0])
           1.2604984471899925))))

(deftest test-connect-all-nodes
  (testing "Testing connecting test-nodes in a blob."
    (is (= (orgpad.render.metaball/connect-all-nodes test-nodes)
           (vector
             {:center [10 5], :size 1.1372516942251183}
             {:center [10 0], :size 1.8644929548312144}
             {:center [0 10], :size 1.3744825889388708}
             {:center [0 0], :size 2.253426498837473})))))

;final
(defn run []
  (do
    (enable-console-print!)
    (run-tests)))

