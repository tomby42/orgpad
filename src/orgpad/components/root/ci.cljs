(ns ^{:doc "CI for root view"}
  orgpad.components.root.ci
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [chan <! >! tap untap close! put! timeout]]
            [orgpad.effects.ci :as eci]
            [orgpad.tools.ci :as tci]
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

   ;; default fallback
   {:regexp #".*",
    :params-name []
    :parser (fn [ch params _]
              (go
                (let [msg (<! ch)]
                  (eci/send-response {:msg-id (:msg-id msg)
                                      :text (:text msg)
                                      :done? true
                                      :response (str "Sorry I can't understand '" (:text msg) "'.")}))))}

   ])

(ci/register-ci :default :default (tci/create-regexp-parser utterance->intent-desc))
