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
   :history [[(now) :init col]]})

(defn- conjs
  [state pth val]
  (if (empty? pth)
    (conj state val)
    (update-in state pth conj val)))

(defn conjes
  "Event store conj"
  [{:keys [state history]} pth val]

  {:state (conjs state pth val)
   :history (conj history [(now) :conj pth val])})

(defn- pops
  [state pth]
   (if (empty? pth)
     (pop state)
     (update-in state pth pop)))

(defn popes
  "Event store pop"
  [{:keys [state history]} pth]

  {:state (pops state pth)
   :history (conj history [(now) :pop pth])})

(defn- assocs
  [state pth key val]
  (if (empty? pth)
    (assoc state key val)
    (update-in state pth assoc key val)))

(defn assoces
  "Event store assoc"
  [{:keys [state history]} pth key val]

  {:state (assocs state pth key val)
   :history (conj history [(now) :assoc pth key val])})

(defn- dissocs
  [state pth key]
  (if (empty? pth)
    (dissoc state key)
    (update-in state pth dissoc key)))

(defn dissoces
  "Event store dissoc"
  [{:keys [state history]} pth key]

  {:state (dissocs state pth key)
   :history (conj history [(now) :dissoc pth key])})

(defn- patchs
  [state pth patch]

  (update-in state pth sdiff/patch patch))

(defn patches
  "String patch"
  [{:keys [state history]} pth patch]

  {:state (patchs state pth patch)
   :history (conj history [(now) :patch pth patch])})


(defn- apply-cmd
  "Apply cmd on state and returns result."
  [state cmd]

  (case (cmd 1)
    :conj (conjs state (cmd 2) (cmd 3))
    :pop (pops state (cmd 2))
    :assoc (assocs state (cmd 2) (cmd 3) (cmd 4))
    :dissoc (dissocs state (cmd 2) (cmd 3))
    :patch (patchs state (cmd 2) (cmd 3))))

(defn state-from-to
  "Apply history starting at 'from' and ending at 'to' to 'state' and
  returns result."
  [state history from to]

  (reduce apply-cmd state (subvec history from to)))

(defn new-state-history
  "Create new state history. State history is sparse representation of
  list of state's history that holds more recent instances of state
  and less older ones. Because we hold journal of changes of state we
  can reconstruct those instances that are not contained in the
  history. State history is hierarchy of buffers where each level
  holds n instances of state with timestamp difference n^level. 'step'
  argument determine size of buffer."
  [step]

  {:step step
   :counts [0]
   :states [[]]})

(defn- propagate-state
  "Updates hierarchy of buffers to fit the condition that each buffers
  holds maximally 'step' items. Returns updated hierarchy."
  [states counts step pos current-states]

  (if (<= (count current-states) step)
    [(assoc states (dec pos) current-states) counts]
    (let [dstep            (dec step)
          state-to-prop    (current-states 0)
          states'          (assoc states (dec pos) (subvec current-states 1))
          counts'          (assoc counts (dec pos) dstep)]
      (if (= (count states) pos)
        [(conj states' [state-to-prop]) (conj counts' dstep)]
        (if (zero? (counts' pos))
          (recur states' (assoc counts' pos dstep) step (inc pos) (conj (states pos) state-to-prop))
          [states' (update-in counts' [pos] dec)])))))

(defn add-state
  "Add new state to the history and returns it."
  [{:keys [step states counts] :as state-history} state stamp]

  (let [[states' counts']       (propagate-state states counts step 1 (conj (states 0) [state stamp]))]
    {:step step
     :counts counts'
     :states states'}))

(defn- find-nearest-state
  [{:keys [states]} stamp cmp-stamps]

  (letfn [(cmp
            [x y]
            (if (vector? x)
              (cmp-stamps (x 1) y)
              (cmp-stamps x (y 1))))]

    (let [s (->>
             states
             (drop-while (fn [x]
                           (== (cmp (x 0) stamp)
                               1)))
             first)
          pos (search/binary-search s stamp cmp)]
      (if (<= 0 pos)
        (s pos)
        (s (dec (- pos)))))))

