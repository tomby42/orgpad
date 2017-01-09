(ns orgpad.test
  (:require [cljs.test :refer-macros [run-all-tests]]
            [doo.runner :refer-macros [doo-tests doo-all-tests]]
            [orgpad.core.store-test]
            [orgpad.core.orgpad-test]
            [orgpad.data.union-find-test]
            [orgpad.dataflow.systems-test]
            [orgpad.tools.bezier-test]
            [orgpad.tools.geom-test]
            [orgpad.tools.order-numbers-test]
            [orgpad.tools.geohash-test]
            ))

(enable-console-print!)

(defn ^:export run
  []
  (run-all-tests #"orgpad.*-test"))

(doo.runner/doo-all-tests #"orgpad.*-test")
