(ns ^{:doc "Default root read/write parser"}
  orgpad.parsers.default-root
  (:require [orgpad.core.store :as store]
            [orgpad.parsers.default :as dp :refer [read mutate updated?]]
            [orgpad.tools.dscript :as ds]
            [orgpad.components.queries :as qs]
            [orgpad.components.registry :as registry]))

(defmethod read :orgpad/root-view
  [{ :keys [state props] :as env } k params]
  (let [db state
        root-unit
        (store/query db [:entity 0])

        root-view-info
        (ds/find-props root-unit (fn [u] (u :orgpad/type) :orgpad/root-unit-view))

        root-info
        (registry/get-component-info :orgpad/root-view)]

    ;; (println "root parser" root-view-info)

    (props (merge env { :view-name (-> root-info :orgpad/default-view-info :orgpad/view-name)
                        :unit-id (-> root-view-info :orgpad/refs first :db/id)
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
