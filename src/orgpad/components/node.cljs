(ns ^{:doc "Node component"}
  orgpad.components.node
  (:require [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [orgpad.components.queries :as qs]
            [orgpad.components.registry :as registry]
            [orgpad.components.sidebar.sidebar :as sidebar]))

(defui Node
  static om/IQuery
  (query
   [this]
   (reduce (fn [q [k info]]
             (assoc q k (-> info :orgpad/class om/get-query)))
           {} (into [] (dissoc (registry/get-registry) :orgpad/root-view))))

  Object
  (render
   [this]
   (let [props (om/props this)
         [[type] unit-info]  (first (drop-while #(= (get % 1) nil) props))
         child-info (registry/get-component-info type)
         child-factory (child-info :orgpad/factory)]
     (child-factory unit-info)
   )
  )
)

(def node (om/factory Node))
