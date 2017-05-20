(ns ^{:doc "CI component registry"}
  orgpad.components.ci.registry)

(def ^:private ci-register (atom {}))

(defn register-ci
  [name context ci]
  (swap! ci-register assoc-in [name context] ci))

(defn get-ci
  [name context]
  (get-in @ci-register [name context] :not-found))
