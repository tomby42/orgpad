(ns ^{:doc "App effects functionality"}
  orgpad.cycle.effects
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [rum.core :as rum]
            [cljs.core.async :refer [chan <! >! tap untap close! put! timeout]]))

(def ^:private effect-chan (chan 10))

(def ^:private effect-task
  (go-loop []
    (let [effects (<! effect-chan)]
      (doseq [effect effects]
        (effect))
      (recur))))

(defn do-effects
  "Realize effects asynchronously"
  [effects]
  (put! effect-chan effects))
