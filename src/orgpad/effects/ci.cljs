(ns ^{:doc "CI core functionality"}
  orgpad.effects.ci
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [chan <! >! tap untap close! put! timeout]]
            [orgpad.components.ci.registry :as ci-reg]))

(def ^:private ci-input-chan (chan 10))
(def ^:private ci-parser-chan (chan 10))
(def ^:private ci-state (atom {:parser-chan nil, :local-context :default, :local-context-state nil}))

(def ^:private ci-task
  (go-loop []
    (let [{:keys [ctx ask? respond] :as msg} (<! ci-input-chan)
          parser-chan (if (:parser-chan @ci-state)
                        (:parser-chan @ci-state)
                        (let [c-prsr (ci-reg/get-ci ctx (:local-context @ci-state))
                              c-prsr-loc-default (ci-reg/get-ci ctx :default)
                              c-prsr-default-loc (ci-reg/get-ci :default (:local-context @ci-state))
                              c-prsr-default (ci-reg/get-ci :default :default)]
                          (or (and (not= c-prsr :not-found)
                                   (c-prsr msg @ci-state))
                              (and (not= c-prsr-loc-default :not-found)
                                   (c-prsr-loc-default msg @ci-state))
                              (and (not= c-prsr-default-loc :not-found)
                                   (c-prsr-default-loc msg @ci-state))
                              (and (not= c-prsr-default :not-found)
                                   (c-prsr-default msg @ci-state)))))
          _ (swap! ci-state assoc :parser-chan parser-chan)
          _ (>! parser-chan (assoc msg :ci-state (:local-context-state @ci-state)))
          resp (<! ci-parser-chan)]
      (swap! ci-state assoc :local-context (or (:local-context resp) :default)
             :local-context-state (or (:local-context-state resp) nil))
      (if (:done? resp)
        (do
          (swap! ci-state assoc :parser-chan nil)
          (respond resp))
        (ask? resp))
      (recur))))

(defn send-msg
  [msg]
  (put! ci-input-chan msg))

(defn send-response
  [resp]
  (put! ci-parser-chan resp))
