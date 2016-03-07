(ns ^{:doc "Definition of atomic view parser"}
  orgpad.parsers.atomic.parser
  (:require [om.next :as om :refer-macros [defui]]
            [orgpad.core.store :as store]
            [orgpad.parsers.default :as dp :refer [read mutate]]
            [orgpad.components.queries :as qs]
            [orgpad.components.registry :as registry]))

(defmethod mutate 'tags/remove
  [{:keys [state]} _ {:keys [db/id orgpad/tags]}]
  {:value {:default []}
   :action
   (fn []
     (swap!
      state
      (fn [db]
        (reduce (fn [new-db tag]
                  (store/transact new-db [[:db/retract id :tags tag]]))
                db tags)
        )))})

(defmethod mutate 'tags/add
  [{:keys [state]} _ {:keys [db/id orgpad/tags]}]
  {:value {:default []}
   :action
   (fn []
     (swap!
      state
      (fn [db]
        (reduce (fn [new-db tag]
                  (store/transact new-db [[:db/add id :tags tag]]))
                db tags)
        )))})
