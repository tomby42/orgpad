(ns ^{:doc "Function tools"}
  orgpad.tools.func
  (:require [orgpad.tools.colls :as colls]))

(defn memoize'
  "Returns a memoized version of a referentially transparent function. The
  memoized version of the function keeps a cache of the mapping from arguments
  to results and, when calls with the same arguments are repeated often, has
  higher performance at the expense of higher memory use."
  [f {:keys [eq-fns key-fn]}]
  (let [mem (volatile! {})]
    (fn [& args]
      (let [key (key-fn args)
            [old-args v] (get @mem key [nil nil])]
        (if (and old-args (colls/semi-shallow-eq old-args args eq-fns))
          v
          (let [ret (apply f args)]
            (vswap! mem assoc key [args ret])
            ret))))))

