(ns ^{:doc "Default root read/write parser"}
  orgpad.parsers.default-root
  (:require [orgpad.core.store :as store]
            [orgpad.core.orgpad :as orgpad]
            [orgpad.parsers.default-unit :as dp :refer [read mutate updated?]]
            [orgpad.tools.dscript :as ds]
            [orgpad.components.registry :as registry]))

(defn- find-root-view-info
  [db]
  (let [root-unit (store/query db [:entity 0])]
    (ds/find-props root-unit (fn [u] (= (u :orgpad/type) :orgpad/root-unit-view)))))

(defmethod read :orgpad/root-view
  [{ :keys [state props] :as env } k params]
  (let [db state

        root-view-info
        (find-root-view-info db)

        root-info
        (registry/get-component-info :orgpad/root-view)

        view-name
        (or (-> root-view-info :orgpad/view-names last first)
            (-> root-info :orgpad/default-view-info :orgpad/view-name))

        view-type
        (or (-> root-view-info :orgpad/view-types last first)
            (-> root-info :orgpad/default-view-info :orgpad/view-type))

        view-path
        (or (-> root-view-info :orgpad/view-paths last first)
            [])

        current-root-id
        (-> root-view-info :orgpad/refs last :db/id)]

;;    (println "root parser" current-root-id view-name view-type view-path)

    (props (merge env { :view-name view-name
                        :unit-id current-root-id
                        :view-type view-type
                        :view-path view-path })
           :orgpad/unit-view params) ))

(defmethod updated? :orgpad/root-view
  [{:keys [value]} { :keys [state] } _]
  (let [root-view-info (find-root-view-info state)
        old-root (-> value :unit :db/id)
        current-root (-> root-view-info :orgpad/refs last :db/id)]
    (not= current-root old-root)))

(defmethod read :orgpad/app-state
  [{ :keys [state] :as env } _ _]
  (-> state (store/query []) first))

(defmethod mutate :orgpad/app-state
  [{:keys [state transact!]} _ [path val]]
  { :state (store/transact state [path val]) })

(defmethod updated? :orgpad/app-state
  [_ { :keys [state] } _]
  (store/changed? state []))

(defmethod mutate :orgpad/root-view-stack
  [{ :keys [state] } _ { :keys [db/id orgpad/view-name orgpad/view-type orgpad/view-path] }]
  (let [root-view-info (find-root-view-info state)
        rvi-id (root-view-info :db/id)]
    { :state (store/transact state [[:db/add rvi-id :orgpad/refs id]
                                    [:db/add rvi-id :orgpad/view-names [view-name id]]
                                    [:db/add rvi-id :orgpad/view-types [view-type id]]
                                    [:db/add rvi-id :orgpad/view-paths [view-path id]]]) }))

(defmethod mutate :orgpad/root-unit-close
  [{ :keys [state] } _ {:keys [db/id orgpad/view-name orgpad/view-type orgpad/view-path] }]
  (let [root-view-info (find-root-view-info state)
        rvi-id (root-view-info :db/id)]
    { :state (store/transact state [[:db/retract rvi-id :orgpad/refs id]
                                    [:db/retract rvi-id :orgpad/view-names [view-name id]]
                                    [:db/retract rvi-id :orgpad/view-types [view-type id]]
                                    [:db/retract rvi-id :orgpad/view-paths [view-path id]] ]) }))

(defmethod mutate :orgpad/root-view-conf
  [{ :keys [state force-update!] } _ [{:keys [unit view path-info] } {:keys [attr value]}]]
  (let [path-info-id (path-info :db/id)]
    (force-update!)
    { :state
      (if path-info-id
        (store/transact state [[:db/add path-info-id attr value]])
        (store/transact state [(merge path-info { :db/id -1
                                                  :orgpad/refs (unit :db/id)
                                                  :orgpad/type :orgpad/unit-path-info
                                                  attr value })
                               [:db/add (unit :db/id) :orgpad/props-refs -1]
                               ]))
     }))

(defmethod mutate :orgpad/root-new-view
  [env _ [unit-tree attr]]
  (-> env
      (mutate :orgpad.units/clone-view [unit-tree (attr :value)])
      (->> (merge env))
      (mutate :orgpad/root-view-conf [unit-tree attr])))

(defmethod mutate :orgpad/save
  [{ :keys [state] } _ storage-el]
  { :state state
    :effect #(orgpad/save-file-by-uri state storage-el) })
