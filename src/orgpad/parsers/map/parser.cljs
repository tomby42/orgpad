(ns ^{:doc "Definition of map view parser"}
  orgpad.parsers.map.parser
  (:require [orgpad.core.store :as store]
            [orgpad.effects.core :as eff]
            [orgpad.components.registry :as registry]
            [orgpad.parsers.default :as dp :refer [read mutate]]))

(defmethod mutate :orgpad.units/new-pair-unit
  [{:keys [state]} _ {:keys [parent position]}]
  (let [info (registry/get-component-info :orgpad/map-view)]
    { :state (store/transact
              state (into
                     [ { :db/id -1
                         :orgpad/type :orgpad/unit
                         :orgpad/refs -3 }

                       (merge { :db/id -2
                                :orgpad/refs -1
                                :orgpad/type :orgpad/unit-view-child
                                :orgpad/unit-position position } (:orgpad/child-props-default info))

                       { :db/id -3
                         :orgpad/type :orgpad/unit }

                       (merge { :db/id -4
                                :orgpad/refs -3
                                :orgpad/type :orgpad/unit-view-child-propagated
                                :orgpad/unit-position position } (:orgpad/child-props-default info)) ]

                     (into [[:db/add 0 :orgpad/refs -1]
                            [:db/add 0 :orgpad/refs -3]]
                           (if (zero? parent)
                             []
                             [[:db/add parent :orgpad/refs -1]])) )) } ))
