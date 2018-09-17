(ns ^{:doc "CI for root view"}
  orgpad.components.root.ci
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [chan <! >! tap untap close! put! timeout]]
            [orgpad.effects.ci :as eci]
            [orgpad.tools.ci :as tci]
            [orgpad.tools.orgpad :as orgpad]
            [orgpad.components.ci.registry :as ci]))

(def ^:private utterance->intent-desc
  [{:regexp #"(?i)^help",
    :params-name []
    :parser (fn [ch params _]
              (go
                (let [msg (<! ch)]
                  (eci/send-response {:local-context :help
                                      :local-context-state (:ci-state msg)
                                      :msg-id (:msg-id msg)
                                      :text (:text msg)
                                      :done? true
                                      :response "What would you like to know?"}))))}

   {:regexp #"(?i)^select\s+units(?:\s+by\s+action\s+(.*))?"
    :params-name [:action]
    :parser (fn [ch params _]
              (go
                (let [msg (<! ch)]
                  (eci/send-response {:local-context :select
                                      :local-context-state {:action (or (:action params)
                                                                        :select)}
                                      :msg-id (:msg-id msg)
                                      :done? true
                                      :text (:text msg)
                                      :response "What do you want to select?"}))))}

   ;; default fallback
   {:regexp #".*",
    :params-name []
    :parser (fn [ch params _]
              (go
                (let [msg (<! ch)]
                  (eci/send-response {:msg-id (:msg-id msg)
                                      :text (:text msg)
                                      :done? true
                                      :response (str "Sorry I can't understand '" (:text msg) "'.")}))))}])

(def ^:private select-utterance->intent-desc
  [{:regexp #"(?i)^quit"
    :params-name []
    :parser (fn [ch params _]
              (go
                (let [msg (<! ch)]
                  (eci/send-response {:msg-id (:msg-id msg)
                                      :text (:text msg)
                                      :done? true
                                      :response "Leaving selection mode."}))))}
   {:regexp #"(.+)"
    :params-name [:selection-text]
    :parser (fn [ch params _]
              (go
                (let [msg (<! ch)]
                  (eci/send-response (merge msg
                                            {:local-context :select
                                             :local-context-state (:ci-state msg)
                                             :done? true
                                             :params params
                                             :action :orgpad.units/select-by-pattern})))))}])

(ci/register-ci :default :default (tci/create-regexp-parser utterance->intent-desc))
(ci/register-ci :default :select (tci/create-regexp-parser select-utterance->intent-desc))
