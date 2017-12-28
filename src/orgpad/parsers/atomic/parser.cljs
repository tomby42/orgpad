(ns ^{:doc "Definition of atomic view parser"}
  orgpad.parsers.atomic.parser
  (:require [orgpad.core.store :as store]
            [orgpad.effects.core :as eff]
            [orgpad.parsers.default-unit :as dp :refer [read mutate]]
            [orgpad.tools.orgpad :as orgpad]))

(defn- update-view-unit
  [db unit-id view key val]
  (store/transact db (orgpad/update-unit-view-query unit-id view key val)))

(defmethod mutate :orgpad.tags/remove
  [{:keys [state]} _ {:keys [orgpad/view orgpad/tags]}]
  { :state (reduce (fn [new-db tag]
                     (store/transact new-db [[:db/retract (view :db/id) :orgpad/tags tag]]))
                   state tags ) } )

(defmethod mutate :orgpad.tags/add
  [{:keys [state]} _ {:keys [db/id orgpad/view orgpad/tags]}]
  { :state (reduce (fn [new-db tag]
                     (update-view-unit new-db id view :orgpad/tags tag))
                   state tags ) } )

(def ^:private desc-update
  (eff/debounce (fn [transact! unit]
                  (transact! [[:orgpad.desc/set unit]])) 0 false) )

(defmethod mutate :orgpad.desc/update
  [{:keys [state transact!]} _ unit]
  { :state state
    :effect (fn []
              (desc-update transact! unit)) } )

(defmethod mutate :orgpad.desc/set
  [{:keys [state transact!]} _ {:keys [db/id orgpad/view orgpad/desc]}]
  { :state (update-view-unit state id view :orgpad/desc desc) } )

(defmethod mutate :orgpad.atom/update
  [{:keys [state transact!]} _ {:keys [db/id orgpad/view orgpad/atom]}]
  (js/console.log ":orgpad.atom/update" id view atom)
  { :state (update-view-unit state id view :orgpad/atom atom) } )
