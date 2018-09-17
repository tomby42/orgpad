(ns ^{:doc "Time tools"}
  orgpad.tools.time)

(defn- now
  "Returns current time"
  []
  (js/Date.))
