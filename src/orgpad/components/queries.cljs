(ns ^{:doc "Common queries"}
  orgpad.components.queries)

(defn- cmeta
  [q x]
  (with-meta q {:component x}))

(defn unit-query[c]
  (cmeta '[:db/id {:orgpad/refs ...} :orgpad/atom :orgpad/type :orgpad/tags :orgpad/desc] c))

(defn unit-view-query[c]
  (cmeta '[:db/id {:orgpad/refs ...} :orgpad/view-type :orgpad/view-path] c))
