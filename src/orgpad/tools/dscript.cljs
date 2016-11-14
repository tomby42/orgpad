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
