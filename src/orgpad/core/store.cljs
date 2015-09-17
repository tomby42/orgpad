(ns ^{:doc "Store with history"}

  orgpad.core.store

  (:require
   [datascript          :as d]
   [orgpad.tools.search :as search]
   [orgpad.tools.time   :as time]
   [wagjo.diff.core     :as sdiff]))

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
  "Returns least nearest pair [state stamp] to 'stamp'. 'cmp-stamps' is custom comparator."
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

(defn new-store
  "Creates new store connected to datascript db throught 'conn'
  with history step 'history-step'"
  [conn history-step]

  (let [history (atom [])
        state-history (atom (new-state-history history-step))]
    (d/listen!
     conn (fn [tx-report]
            (println tx-report)
            (swap! history conj [(time/now) (:tx-data tx-report)])
            (swap! state-history add-state
                   (:db-after tx-report)
                   (count @history))))
    {:state conn
     :history history
     :state-history state-history}))
