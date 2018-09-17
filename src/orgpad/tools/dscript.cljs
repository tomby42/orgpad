(ns ^{:doc "Datascript tools"}
  orgpad.tools.dscript
  (:require    [datascript.core          :as ds]
               [datascript.db            :as db]))

(defn entity->map
  [e]
  (when e
    (into {:db/id (.-eid e)} e)))

(defn find-props-base-all
  [u pred]
  (->> u :orgpad/props-refs (filter pred)))

(defn find-props-base
  [u pred]
  (-> u (find-props-base-all pred) first))

(defn find-props
  [u pred]
  (entity->map (find-props-base u pred)))

(defn find-props-all
  [u pred]
  (mapv entity->map (find-props-base-all u pred)))

(defn props->maps
  [u]
  (mapv entity->map (:orgpad/props-refs u)))
