(ns ^{:doc "Event sourcing library. More doc see for example
    http://docs.geteventstore.com/introduction/event-sourcing-basics/ "}

    orgpad.core.event-store

    (:require [orgpad.tools.search :as search]
              [wagjo.diff.core     :as sdiff]))

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

(defn- patchs
  [state pth patch]

  (update-in state pth sdiff/patch patch))

(defn patches
  "String patch"
  [{:keys [state history]} pth patch]

  {:state (patchs state pth patch)
   :history (conj history [:patch pth patch (now)])})


(defn- apply-cmd
  "Apply cmd on state and returns result."
  [state cmd]

  (case (cmd 0)
    :conj (conjs state (cmd 1) (cmd 2))
    :pop (pops state (cmd 1))
    :assoc (assocs state (cmd 1) (cmd 2) (cmd 3))
    :dissoc (dissocs state (cmd 1) (cmd 2))
    :patch (patchs state (cmd 1) (cmd 2))))

(defn state-from-to
  "Apply history starting at 'from' and ending at 'to' to 'state' and
  returns result."
  [state history from to]

  (reduce apply-cmd state (subvec history from to)))

(defn new-state-history
  [step]

  {:step step
   :counts [0]
   :states [[]]})

(defn- propagate-state
  [states counts step pos current-states]

  (if (< (count current-states) (inc step))
    [(assoc states (dec pos) current-states) counts]
    (let [state-to-prop    (current-states 0)
          states'          (assoc states (dec pos) (subvec current-states 1))
          counts'          (assoc counts (dec pos) step)]
      (if (= (count states) pos)
        [(conj states' [state-to-prop]) (conj counts' step)]
        (if (zero? (counts pos))
          (recur states' counts' step (inc pos) (conj (states pos) state-to-prop))
          [states' (update-in counts' [pos] dec)])))))

(defn add-state
  [{:keys [step states counts] :as state-history} state stamp]

  (let [[states' counts']       (propagate-state states counts step 1 (conj (states 0) state))]
    {:step step
     :counts counts'
     :states states'}))
