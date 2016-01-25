(ns ^{:doc "App life cycle functionality"}
  orgpad.cycle.life
  (:require
   [om.next :as om]))

(defn create-cycle
  [initial-state read-fn mutate-fn root-el root-component]

  (let [reconciler
        (om/reconciler
         {:state     (atom initial-state)
          :normalize false
          :parser    (om/parser {:read read-fn :mutate mutate-fn})})]
    (om/add-root! reconciler
                  root-component root-el)
    reconciler))
