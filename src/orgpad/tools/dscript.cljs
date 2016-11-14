(ns ^{:doc "Datascript tools"}

  orgpad.tools.dscript

  (:require    [datascript.core          :as ds]
               [datascript.db            :as db]))

(defn entity->map
  [e]
  (when e
    (into { :db/id (.-eid e) } e)))

(defn find-props
  [u pred]
  (->> u
       :orgpad/props-refs
       (filter pred)
       first
       entity->map))

(defn- sort-refs
  [unit]
  (into [] (sort #(compare (-> %1 :unit :db/id) (-> %2 :unit :db/id)) (unit :orgpad/refs))))
