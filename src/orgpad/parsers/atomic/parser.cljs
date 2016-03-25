(ns ^{:doc "Definition of atomic view parser"}
  orgpad.parsers.atomic.parser
  (:require [orgpad.core.store :as store]
            [orgpad.parsers.default :as dp :refer [read mutate]]))

(defmethod mutate :tags/remove
  [{:keys [state]} _ {:keys [db/id orgpad/tags]}]
  { :state (reduce (fn [new-db tag]
                     (store/transact new-db [[:db/retract id :orgpad/tags tag]]))
                   state tags ) } )

(defmethod mutate :tags/add
  [{:keys [state]} _ {:keys [db/id orgpad/tags]}]
  { :state (reduce (fn [new-db tag]
                     (store/transact new-db [[:db/add id :orgpad/tags tag]]))
                   state tags ) } )
