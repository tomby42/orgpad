(ns ^{:doc "Default root read/write parser"}
  orgpad.parsers.default-root
  (:require [om.next :as om]
            [orgpad.core.store :as store]
            [orgpad.parsers.default :as dp :refer [read mutate updated?]]
            [orgpad.components.queries :as qs]
            [orgpad.components.registry :as registry]))

(defmethod read :orgpad/root-view
  [{ :keys [state props] :as env } k params]
  (let [db state
        [root-view-info]
        (store/query db '[ :find [(pull ?e [:orgpad/refs]) ...]
                           :where [?e :orgpad/type :orgpad/root-unit-view] ])

        root-info
        (registry/get-component-info :orgpad/root-view)]

    (println "root parser" root-view-info)

    (props (merge env { :view-name (-> root-info :orgpad/default-view-info :orgpad/view-name)
                        :unit-id (get-in root-view-info [:orgpad/refs 0 :db/id])
                        :view-type (-> root-info :orgpad/default-view-info :orgpad/view-type)
                        :view-path [] })
           :orgpad/unit-view params) ))

(defmethod read :orgpad/app-state
  [{ :keys [state] :as env } _ _]
  (-> state (store/query []) first))

(defmethod mutate :orgpad/app-state
  [{:keys [state transact!]} _ [path val]]
  { :state (store/transact state [path val]) })

(defmethod updated? :orgpad/app-state
  [_ { :keys [state] }]
  (store/changed? state []))
