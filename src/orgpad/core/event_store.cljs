(ns ^{:doc "Event sourcing library. More doc see for example
    http://docs.geteventstore.com/introduction/event-sourcing-basics/ "}

    orgpad.core.event-store

    (:require [orgpad.core.search :as search]))

(defn- now
  "Returns current time"
  []

  (js/Date.))

(defn new-event-store
  "Creates new event store with col as initial state"
  [col]

  {:state col
   :history [[:init col (now)]]})

(defn- conjs
  [state pth val]
  (if (empty? pth)
    (conj state val)
    (update-in state pth conj val)))

(defn conjes
  "Event store conj"
  [{:keys [state history]} pth val]

  {:state (conjs state pth val)
   :history (conj history [:conj pth val (now)])})

(defn- pops
  [state pth]
   (if (empty? pth)
     (pop state)
     (update-in state pth pop)))

(defn popes
  "Event store pop"
  [{:keys [state history]} pth]

  {:state (pops state pth)
   :history (conj history [:pop pth (now)])})

(defn- assocs
  [state pth key val]
  (if (empty? pth)
    (assoc state key val)
    (update-in state pth assoc key val)))

(defn assoces
  "Event store assoc"
  [{:keys [state history]} pth key val]

  {:state (assocs state pth key val)
   :history (conj history [:assoc pth key val (now)])})

(defn- dissocs
  [state pth key]
  (if (empty? pth)
    (dissoc state key)
    (update-in state pth dissoc key)))

(defn dissoces
  "Event store dissoc"
  [{:keys [state history]} pth key]
  
  {:state (dissocs state pth key)
   :history (conj history [:dissoc pth key (now)])})

(defn- apply-cmd 
  [state cmd]

  (case (cmd 0)
    :conj (conjs state (cmd 1) (cmd 2))
    :pop (pops state (cmd 1))
    :assoc (assocs state (cmd 1) (cmd 2) (cmd 3))
    :dissoc (dissocs state (cmd 1) (cmd 2))))

(defn state-from-to
  [state history from to]

  (reduce apply-cmd state (subvec history from to)))
