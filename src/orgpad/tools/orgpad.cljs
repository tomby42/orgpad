(ns ^{:doc "Orgpad tools"}
  orgpad.tools.orgpad)

(defn uid
  [unit]
  (-> unit :unit :db/id))

(defn sort-refs
  [unit]
  (into [] (sort #(compare (uid %1) (uid %2)) (unit :orgpad/refs))))

(defn pid
  [parent-view]
  (-> parent-view :orgpad/refs first :db/id))

(defn view-name
  [unit]
  (-> unit :view :orgpad/view-name))

(defn refs-count
  [unit]
  (-> unit :unit :orgpad/refs count))

(defn get-sorted-ref
  [unit idx]
  (get (sort-refs unit) idx))

(defn update-unit-view-query
  [unit-id view key val]
  (if (view :db/id)
    [[:db/add (view :db/id) key val]]
    [(merge view
            { :db/id -1
              :orgpad/refs unit-id
              key val
              :orgpad/type :orgpad/unit-view })
     [:db/add unit-id :orgpad/props-refs -1] ]))
