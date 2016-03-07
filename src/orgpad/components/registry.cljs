(ns ^{:doc "Component registry"}
  orgpad.components.registry)

(def ^:private component-register (atom {}))

(defn register-component-info
  [name component-info]

  (swap! component-register assoc name component-info))

(defn get-component-info
  [name]

  (get @component-register name :not-found))

(defn get-registry
  []

  @component-register)
