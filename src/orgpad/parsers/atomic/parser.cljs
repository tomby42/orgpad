(ns ^{:doc "Definition of atomic view parser"}
  orgpad.parsers.atomic.parser
  (:require [orgpad.core.store :as store]
            [orgpad.effects.core :as eff]
            [orgpad.parsers.default-unit :as dp :refer [read mutate]]
            [orgpad.tools.orgpad :as orgpad]
            [orgpad.tools.orgpad-db :as otdb]
            [orgpad.tools.dom :as dom]
            [orgpad.tools.geom :as geom]))

(defn- update-view-unit
  [db unit-id view key val & [qry]]
  (store/transact db (into (orgpad/update-unit-view-query unit-id view key val) qry)))

(defmethod mutate :orgpad.tags/remove
  [{:keys [state]} _ {:keys [orgpad/view orgpad/tags]}]
  {:state (reduce (fn [new-db tag]
                    (store/transact new-db [[:db/retract (view :db/id) :orgpad/tags tag]]))
                  state tags)})

(defmethod mutate :orgpad.tags/add
  [{:keys [state]} _ {:keys [db/id orgpad/view orgpad/tags]}]
  {:state (reduce (fn [new-db tag]
                    (update-view-unit new-db id view :orgpad/tags tag))
                  state tags)})

(def ^:private desc-update
  (eff/debounce (fn [transact! unit]
                  (transact! [[:orgpad.desc/set unit]])) 200 false))

(defmethod mutate :orgpad.desc/update
  [{:keys [state transact!]} _ unit]
  {:state state
   :effect (fn []
             (desc-update transact! unit))})

(defmethod mutate :orgpad.desc/set
  [{:keys [state transact!]} _ {:keys [db/id orgpad/view orgpad/desc]}]
  {:state (update-view-unit state id view :orgpad/desc desc)})

(defn- compute-size-stats
  [width height]
  (let [ratio (/ width height)
        abs (fn [v] (if (< v 0) (- v) v))
        error (abs (- 2.0 ratio))]
    {:width width
     :height height
     :ratio ratio
     :error error}))

(defn compute-optimal-size
  [sizes]
  (let [stats (map #(compute-size-stats (% 0) (% 1)) sizes)
        optimal-height (:height (apply (partial min-key :error) stats))
        optimal-width (:width (apply (partial min-key :width)
                                     (filter #(= (:height %) optimal-height) stats)))
        optimal-height' (max optimal-height 30)
        optimal-width' (max optimal-width 30)]
    (js/console.log stats)
    [optimal-width' optimal-height']))

(defmethod mutate :orgpad.atom/update
  [{:keys [state transact!]} _ {:keys [db/id orgpad/view orgpad/atom]}]
  (let [view' (if (:db/id view)
                view
                (or
                 (store/query state '[:find (pull ?v [*]) .
                                      :in $ ?u ?name ?type
                                      :where
                                      [?u :orgpad/props-refs ?v]
                                      [?v :orgpad/type :orgpad/unit-view]
                                      [?v :orgpad/view-name ?name]
                                      [?v :orgpad/view-type ?type]]
                              [id (:orgpad/view-name view) (:orgpad/view-type view)])
                 view))
        sizes (:bbs (first (dom/get-html-sizes [{:html atom :id id :widths (range 0 1025 25)}])))
        size (compute-optimal-size sizes)
        size-qry (otdb/update-vsize-qry state id (:orgpad/view-name view') size)]
    ;; (js/console.log "atom update - qry size update" size-qry size state)
    ;; TODO update geocache
    (js/console.log "Autoresizing to " size)
    {:state (update-view-unit state id view' :orgpad/atom atom size-qry)}))
