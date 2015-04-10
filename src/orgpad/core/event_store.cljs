(ns ^{:doc "Event sourcing library. More doc see for example
    http://docs.geteventstore.com/introduction/event-sourcing-basics/ "}

    orgpad.core.event-store)

(defn- now
  "Returns current time"
  []

  (js/Date.))

(defn new-event-store
  "Creates new event store with col as initial state"
  [col]

  {:state col
   :history [[:init col (now)]]})

(defn- get-update-fn
  "Returns update function depending on command cmd"
  [cmd]

  (case cmd
    :conj conj
    :disj disj
    :assoc assoc
    :dissoc dissoc))

(defn update
  "Applies events to event-store and returns a result."
  [event-store events]

  (let [t (now)]
    (reduce (fn [{:keys [state history]} [cmd idx val]]
              (let [update-fn (get-update-fn cmd)]
                {:state (update-in state idx update-fn val)
                 :history (conj history [cmd idx val t])}))
            event-store events)))
