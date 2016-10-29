(ns orgpad.test
  (:require [cljs.test :refer-macros [run-all-tests]]
            [orgpad.core.store-test]
            [orgpad.core.orgpad-test]
            [orgpad.data.union-find-test]
            [orgpad.dataflow.systems-test]
            ))

(enable-console-print!)

(defn ^:export run
  []
  (run-all-tests #"orgpad.*-test"))
