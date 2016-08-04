(ns ^{:doc "Collection tools macros"}
  orgpad.tools.colls)

(defmacro >-
  [f & args]
  (loop [f f, args args]
    (if args
      (let [arg (first args)
            threaded (if (seq? arg)
                       (with-meta `(~f ~@arg) (meta arg))
                       (list f arg))]
        (recur threaded (next args)))
      f)))
