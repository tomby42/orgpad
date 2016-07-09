(ns ^{:doc "Definition of atomic view parser"}
  orgpad.parsers.atomic.parser
  (:require [orgpad.core.store :as store]
            [orgpad.effects.core :as eff]
            [orgpad.parsers.default-unit :as dp :refer [read mutate]]))

(defn- update-view-unit
  [db unit-id view key val]
  (store/transact db
                  (if (view :db/id)
                    [[:db/add (view :db/id) key val]]
                    [(merge view
                            { :db/id -1
                              :orgpad/refs unit-id
                              key val
                              :orgpad/type :orgpad/unit-view })
                     [:db/add unit-id :orgpad/props-refs -1] ])))

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
                  (transact! [[:orgpad.desc/set unit]])) 500 false) )

(defmethod mutate :orgpad.desc/update
  [{:keys [state transact!]} _ unit]
  { :state state
    :effect (fn []
              (desc-update transact! unit)) } )

(defmethod mutate :orgpad.desc/set
  [{:keys [state transact!]} _ {:keys [db/id orgpad/view orgpad/desc]}]
  { :state (update-view-unit state id view :orgpad/desc desc) } )

(def ^:private atom-update
  (eff/debounce (fn [transact! unit]
                  (transact! [[:orgpad.atom/set unit]])) 500 false) )

(defmethod mutate :orgpad.atom/update
  [{:keys [state transact!]} _ unit]
  { :state state
    :effect (fn []
              (atom-update transact! unit)) } )

(defmethod mutate :orgpad.atom/set
  [{:keys [state transact!]} _ {:keys [db/id orgpad/view orgpad/atom]}]
  { :state (update-view-unit state id view :orgpad/atom atom) } )
