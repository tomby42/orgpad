(ns ^{:doc "Definition of jupyter view parser"}
  orgpad.parsers.jupyter.parser
  (:require [orgpad.core.store :as store]
            [orgpad.effects.core :as eff]
            [orgpad.parsers.default-unit :as dp :refer [read mutate]]
            [orgpad.tools.orgpad :as orgpad]
            [orgpad.effects.jupyter :as jupyter]))

(defmethod mutate :orgpad.jupyter/update
  [{:keys [state]} _ {:keys [db/id orgpad/view key val]}]
  (println "jupyter update" id view key val (orgpad/update-unit-view-query id view key val))
  {:state (store/transact state (orgpad/update-unit-view-query id view key val))})

(defn- exec
  [transact! id view codes url]
  (jupyter/exec-code url (get codes 0)
                     (fn [res]
                       (when (and res (contains? #{"execute_result" "display_data"} (aget res "msg_type")))
                         (transact! [[:orgpad.jupyter/result
                                      {:id id
                                       :view view
                                       :msg-type (aget res "msg_type")
                                       :data (js->clj (aget res "content" "data"))}]])))))

(defmethod mutate :orgpad.jupyter/result
  [{:keys [state]} _ {:keys [id view msg-type data]}]
  (let [vid (:db/id view)
        results (store/query state '[:find ?r .
                                     :in $ ?vid
                                     :where
                                     [?vid :orgpad/jupyter-results ?r]] [vid])]
    {:state (store/transact state [[:db/add vid :orgpad/jupyter-results (conj results data)]])}))

(defmethod mutate :orgpad.jupyter/exec
  [{:keys [state transact!]} _ {:keys [id view codes url]}]
  {:state (store/transact state [[:db/add (:db/id view) :orgpad/jupyter-results []]])
   :effect #(exec transact! id view codes url)})
