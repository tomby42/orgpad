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
