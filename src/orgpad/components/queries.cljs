(ns ^{:doc "Common queries"}
  orgpad.components.queries)

(defn- cmeta
  [q x]
  (with-meta q {:component x}))

(defn unit-query [c]
  (cmeta '[:db/id {:orgpad/refs ...} :orgpad/atom :orgpad/type :orgpad/tags :orgpad/desc] c))

(defn unit-view-query [c]
  (cmeta '[:db/id {:orgpad/refs ...} :orgpad/view-type :orgpad/view-name] c))

(defn unit-map-view-query [c]
  (cmeta '[:db/id {:orgpad/refs ...} :orgpad/view-type :orgpad/view-name :orgpad/transform] c))


(defn unit-map-child-view-query [c]
  (cmeta '[:db/id {:orgpad/refs ...} :orgpad/view-type :orgpad/view-name :orgpad/unit-width
           :orgpad/unit-height :orgpad/unit-border-color :orgpad/unit-bg-color :orgpad/unit-border-width
           :orgpad/unit-corner-x :orgpad/unit-corner-y :orgpad/unit-border-style :orgpad/unit-position] c))

(defn unit-map-tuple-view-query [c]
  (cmeta '[:db/id {:orgpad/refs ...} :orgpad/view-type :orgpad/view-name :orgpad/active-unit] c))
