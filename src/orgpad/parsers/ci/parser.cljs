(ns ^{:doc "Definition of ci parser"}
  orgpad.parsers.ci.parser
  (:require [orgpad.core.store :as store]
            [orgpad.effects.core :as eff]
            [orgpad.parsers.default-unit :as dp :refer [read mutate updated?]]
            [orgpad.tools.orgpad :as orgpad]
            [orgpad.effects.ci :as ci]))

(defn- ask?
  [transact! response]
  (transact! [[:orgpad.ci/update-msg response]]))

(defn- respond
  [transact! response]
  (transact! [[:orgpad.ci/run-ation response]]))

(defmethod mutate :orgpad.ci/send-msg
  [{:keys [state transact!]} _ msg]
  (let [state' (store/transact state [{:db/id (or (:msg-id msg) -1)
                                       :orgpad/type :orgpad/msg
                                       :orgpad/text (:text msg)
                                       :orgpad/response "Processing..."}] {:cumulative-changes true :tempids true})
        msg' (assoc msg
                    :msg-id (or (:msg-id msg) (-> state' store/tempids (get -1)))
                    :ask? (partial ask? transact!)
                    :respond (partial respond transact!))]
    { :state state'
      :effect #(ci/send-msg msg')}))

(defmethod mutate :orgpad.ci/update-msg
  [{:keys [state]} _ response]
  { :state (store/transact state [{:db/id (:msg-id response)
                                   :done? false
                                   :orgpad/type :orgpad/msg
                                   :orgpad/text (:text response)
                                   :orgpad/response (:response response)}] {:cumulative-changes true}) })

(defmethod mutate :orgpad.ci/run-ation
  [{:keys [state] :as env} _ response]
  (let [minfo (if (:action response)
                (mutate env (:action response) response)
                {:state state})]
    {:state (store/transact (:state minfo) [{:db/id (:msg-id response)
                                             :done? true
                                             :orgpad/text (:text response)
                                             :orgpad/response (:response response)
                                             :orgpad/parameters (or (:parameters response) {})}])
     :effect (:effect minfo)}))

(defmethod read :orgpad.ci/msg-list
  [{ :keys [state] :as env } _ params]
  (let [msgs (store/query state '[:find ?id ?text ?response
                                  :in $
                                  :where
                                  [?id :orgpad/type :orgpad/msg]
                                  [?id :orgpad/text ?text]
                                  [?id :orgpad/response ?response]])]
    (println "msgs: " msgs)
    (map #(zipmap [:db/id :orgpad/text :orgpad/response] %) msgs)))

(defmethod updated? :orgpad.ci/msg-list
  [_ { :keys [state] } _]
  (store/changed? state '[:find ?id
                          :in $
                          :where
                          [?id :orgpad/response]]))
