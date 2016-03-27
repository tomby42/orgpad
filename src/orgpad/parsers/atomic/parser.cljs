(ns ^{:doc "Definition of atomic view parser"}
  orgpad.parsers.atomic.parser
  (:require [orgpad.core.store :as store]
            [orgpad.effects.core :as eff]
            [orgpad.parsers.default :as dp :refer [read mutate]]))

(defmethod mutate :orgpad.tags/remove
  [{:keys [state]} _ {:keys [db/id orgpad/tags]}]
  { :state (reduce (fn [new-db tag]
                     (store/transact new-db [[:db/retract id :orgpad/tags tag]]))
                   state tags ) } )

(defmethod mutate :orgpad.tags/add
  [{:keys [state]} _ {:keys [db/id orgpad/tags]}]
  { :state (reduce (fn [new-db tag]
                     (store/transact new-db [[:db/add id :orgpad/tags tag]]))
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
  [{:keys [state transact!]} _ {:keys [db/id orgpad/desc]}]
  { :state (store/transact state [[:db/add id :orgpad/desc desc]]) } )

(def ^:private atom-update
  (eff/debounce (fn [transact! unit]
                  (transact! [[:orgpad.atom/set unit]])) 500 false) )

(defmethod mutate :orgpad.atom/update
  [{:keys [state transact!]} _ unit]
  { :state state
    :effect (fn []
              (atom-update transact! unit)) } )

(defmethod mutate :orgpad.atom/set
  [{:keys [state transact!]} _ {:keys [db/id orgpad/atom]}]
  { :state (store/transact state [[:db/add id :orgpad/atom atom]]) } )
