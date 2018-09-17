(ns ^{:doc "CI for map view"}
  orgpad.components.map.ci
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [chan <! >! tap untap close! put! timeout]]
            [orgpad.effects.ci :as eci]
            [orgpad.tools.ci :as tci]
            [orgpad.components.ci.registry :as ci]))

(def ^:private utterance->intent-desc
  [{:regexp #"(?i)^add unit(?:\s+(.*))?",
    :params-name [:position]
    :parser (fn [ch params _]
              (go
                (let [msg (<! ch)
                      _ (eci/send-response {:msg-id (:msg-id msg)
                                            :text (:text msg)
                                            :done? false
                                            :response "Where ?"})
                      resp (<! ch)]
                  (eci/send-response {:msg-id (:msg-id msg)
                                      :text (str (:text msg) " " (:text resp))
                                      :done? true
                                      :response (str "Unit added at " (:text resp))}))))}])

(ci/register-ci :orgpad/map-view :default (tci/create-regexp-parser utterance->intent-desc))
