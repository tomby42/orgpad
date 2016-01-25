(ns ^{:doc "Common queries"}
  orgpad.components.queries)

(defn- cmeta
  [q x]
  (with-meta q {:component x}))

(def unit-query
  (cmeta '[:db/id {:orgpad/refs ...} :orgpad/atom :orgpad/type] :unit))

(def unit-view-query
  (cmeta '[:db/id {:orgpad/refs ...} :orgpad/view-type :orgpad/view-path] :view))
