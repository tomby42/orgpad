(ns ^{:doc "Effects core functionality"}
  orgpad.effects.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [chan <! >! tap untap close! put! timeout]]))

(defn debounce
  "Returns a function, that, as long as it continues to be invoked,
  will not be triggered.  The function will be called after it stops
  being called for N milliseconds. If 'immediate' is passed, trigger
  the function on the leading edge, instead of the trailing."

  [f bounce-timeout immediate?]

  (let [state (atom nil)]
    (fn [& args]
      (let [call-now?  (and
                        immediate?
                        (not @state) )
            later-fn
            (fn []
              (reset! state nil)
              (when (not call-now?) (apply f args)) ) ]

        (js/clearTimeout @state)
        (reset! state (js/setTimeout later-fn bounce-timeout))
        (when call-now? (apply f args)) )
      )))
