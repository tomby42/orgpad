(ns ^{:doc "Definition of map view parser"}
  orgpad.parsers.map.parser
  (:require [orgpad.core.store :as store]
            [orgpad.effects.core :as eff]
            [orgpad.components.registry :as registry]
            [orgpad.parsers.default :as dp :refer [read mutate]]))

(defmethod mutate :orgpad.units/new-pair-unit
  [{:keys [state]} _ {:keys [parent position transform]}]
  (let [info (registry/get-component-info :orgpad/map-view)
        {:keys [translate scale]} transform
        pos  [(- (* (:center-x position) scale) (translate 0))
              (- (* (:center-y position) scale) (translate 1))]]
    { :state (store/transact
              state (into
                     [ { :db/id -1
                         :orgpad/type :orgpad/unit
                         :orgpad/props-refs -2
                         :orgpad/refs -3 }

                       (merge { :db/id -2
                                :orgpad/refs -1
                                :orgpad/type :orgpad/unit-view-child
                                :orgpad/unit-position pos } (:orgpad/child-props-default info))

                       { :db/id -3
                         :orgpad/props-refs -4
                         :orgpad/type :orgpad/unit }

                       (merge { :db/id -4
                                :orgpad/refs -3
                                :orgpad/type :orgpad/unit-view-child-propagated
                                :orgpad/unit-position pos } (:orgpad/child-props-default info)) ]

                     (into [[:db/add 0 :orgpad/refs -1]
                            [:db/add 0 :orgpad/refs -3]]
                           (if (zero? parent)
                             []
                             [[:db/add parent :orgpad/refs -1]])) )) } ))

(defmethod mutate :orgpad.units/map-view-canvas-move
  [{:keys [state]} _ {:keys [view unit-id old-pos new-pos]}]
  (let [id (view :db/id)
        transform (view :orgpad/transform)
        {:keys [translate scale]} transform
        new-translate [(+ (translate 0) (/ (- (new-pos 0) (old-pos 0)) scale))
                       (+ (translate 1) (/ (- (new-pos 1) (old-pos 1)) scale))]
        new-transformation (merge transform { :translate new-translate })]
;;    (println id view transform new-translate new-transformation old-pos new-pos)
    { :state (if (nil? id)
               (store/transact state [(merge view { :db/id -1
                                                    :orgpad/refs unit-id
                                                    :orgpad/transform new-transformation
                                                    :orgpad/type :orgpad/unit-view })
                                      [:db/add unit-id :orgpad/props-refs -1]])
               (store/transact state [[:db/add id :orgpad/transform new-transformation]])) } ))
