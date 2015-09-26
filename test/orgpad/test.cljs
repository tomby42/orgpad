(ns orgpad.test
  (:require [cljs.test :refer-macros [run-all-tests]]
            [orgpad.core.store-test]
            ))

(enable-console-print!)

(defn ^:export run
  []
  (run-all-tests #"orgpad.*-test"))
