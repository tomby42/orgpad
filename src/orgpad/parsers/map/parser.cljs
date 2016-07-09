(ns ^{:doc "Definition of map view parser"}
  orgpad.parsers.map.parser
  (:require [orgpad.core.store :as store]
            [orgpad.effects.core :as eff]
            [orgpad.components.registry :as registry]
            [orgpad.tools.colls :as colls]
            [orgpad.parsers.default-unit :as dp :refer [read mutate]]))

(def ^:private propagated-query
  '[:find [(pull ?p ?selector) ...]
    :in $ ?e ?type ?selector
    :where
    [?p :orgpad/view-type ?type]
    [?p :orgpad/refs ?e]])

(defn- prepare-propagated-props
  [db unit-id props-from-children]
  (let [indexer (volatile! -2)
        props-units-transducer
        (comp
         (mapcat (fn [[type props]]
                   (store/query db propagated-query
                                [unit-id type props])))
         (map (fn [prop-unit]
                (merge prop-unit
                       { :db/id (vswap! indexer dec)
                         :orgpad/type :orgpad/unit-view-child-propagated
                         :orgpad/refs -1 }))))]
    (into [] props-units-transducer props-from-children)))

(defmethod mutate :orgpad.units/new-sheet
  [{:keys [state]} _ {:keys [unit view params]}]
  (let [unit-id (unit :db/id)
        info (registry/get-component-info (view :orgpad/view-type))
        propagated-refs (prepare-propagated-props
                         state unit-id
                         (info :orgpad/propagated-props-from-children))
        t (colls/minto
           [{ :db/id -1
              :orgpad/type :orgpad/unit
              :orgpad/props-refs (into [] (map :db/id) propagated-refs) }
            (if (-> :db/id view nil?)
              (merge view
                     { :db/id -2
                       :orgpad/type :orgpad/unit-view
                       :orgpad/refs unit-id
                       :orgpad/active-unit (count (unit :orgpad/refs)) })
              [:db/add (view :db/id) :orgpad/active-unit (count (unit :orgpad/refs))])
            [:db/add 0 :orgpad/refs -1]]
           propagated-refs
           (if (-> :db/id view nil?) [[:db/add unit-id :orgpad/props-refs -2]] [])
           (if (zero? unit-id) [] [[:db/add unit-id :orgpad/refs -1]]))]
          { :state (store/transact state t) }))

(defmethod mutate :orgpad.units/new-pair-unit
  [{:keys [state]} _ {:keys [parent position transform view-name]}]
  (let [info (registry/get-component-info :orgpad/map-view)
        {:keys [translate scale]} transform
        pos  [(- (* (:center-x position) scale) (translate 0))
              (- (* (:center-y position) scale) (translate 1))]]
    { :state (store/transact
              state (into
                     [ { :db/id -1
                         :orgpad/type :orgpad/unit
                         :orgpad/props-refs -2
                         :orgpad/refs -3 }

                       (merge (:orgpad/child-props-default info)
                              { :db/id -2
                                :orgpad/refs -1
                                :orgpad/type :orgpad/unit-view-child
                                :orgpad/view-name view-name
                                :orgpad/unit-position pos } )

                       { :db/id -3
                         :orgpad/props-refs -4
                         :orgpad/type :orgpad/unit }

                       (merge (:orgpad/child-props-default info)
                              { :db/id -4
                                :orgpad/refs -3
                                :orgpad/type :orgpad/unit-view-child-propagated
                                :orgpad/view-name view-name
                                :orgpad/unit-position pos } )

                      [:db/add 0 :orgpad/refs -1]
                      [:db/add 0 :orgpad/refs -3]]
                     (if (zero? parent)
                       []
                       [[:db/add parent :orgpad/refs -1]]))) } ))

(defn- compute-translate
  [translate scale new-pos old-pos]
  [(+ (translate 0) (/ (- (new-pos 0) (old-pos 0)) scale))
   (+ (translate 1) (/ (- (new-pos 1) (old-pos 1)) scale))])

(defmethod mutate :orgpad.units/map-view-canvas-move
  [{:keys [state]} _ {:keys [view unit-id old-pos new-pos]}]
  (let [id (view :db/id)
        transform (view :orgpad/transform)
        new-translate (compute-translate (transform :translate)
                                         (transform :scale)
                                         new-pos old-pos)
        new-transformation (merge transform { :translate new-translate })]
;;    (println id view transform new-translate new-transformation old-pos new-pos)
    { :state (if (nil? id)
               (store/transact state [(merge view { :db/id -1
                                                    :orgpad/refs unit-id
                                                    :orgpad/transform new-transformation
                                                    :orgpad/type :orgpad/unit-view })
                                      [:db/add unit-id :orgpad/props-refs -1]])
               (store/transact state [[:db/add id :orgpad/transform new-transformation]])) } ))


(defmethod mutate :orgpad.units/map-view-unit-move
  [{:keys [state]} _ {:keys [prop parent-view unit-tree old-pos new-pos]}]
  (let [id (prop :db/id)
        unit-id (-> unit-tree :unit :db/id)
        new-translate (compute-translate (prop :orgpad/unit-position)
                                         (-> parent-view :orgpad/transform :scale)
                                         new-pos old-pos)]
;;    (println id prop translate new-translate old-pos new-pos)
    { :state (if (nil? id)
               (store/transact state [(merge prop { :db/id -1
                                                    :orgpad/refs unit-id
                                                    :orgpad/unit-position new-translate
                                                    :orgpad/type :orgpad/unit-view-child })
                                      [:db/add unit-id :orgpad/props-refs -1]])
               (store/transact state [[:db/add id :orgpad/unit-position new-translate]])) } ))

(defn- propagated-prop
  [{:keys [unit view props]} prop]
  (if (-> unit :orgpad/refs empty? not)
    (let [child-unit (-> unit :orgpad/refs (nth (view :orgpad/active-unit)))
          prop (first
                (filter (fn [p]
                          (and p
                               (= (p :orgpad/view-name) (view :orgpad/view-name))
                               (= (p :orgpad/type) :orgpad/unit-view-child-propagated)))
                       (child-unit :props)))]
      [child-unit prop])
    [nil nil]))

(defn- update-size
  [state id unit-id new-size type prop]
  (if (nil? id)
    (store/transact state [(merge prop { :db/id -1
                                         :orgpad/refs unit-id
                                         :orgpad/unit-width (new-size 0)
                                         :orgpad/unit-height (new-size 1)
                                         :orgpad/type type })
                           [:db/add unit-id :orgpad/props-refs -1]])
    (store/transact state [[:db/add id :orgpad/unit-width (new-size 0)]
                           [:db/add id :orgpad/unit-height (new-size 1)]])))

(defmethod mutate :orgpad.units/map-view-unit-resize
  [{:keys [state]} _ {:keys [prop parent-view unit-tree old-pos new-pos]}]
  (let [id (prop :db/id)
        info (registry/get-component-info (-> unit-tree :view :orgpad/view-type))
        [propagated-unit propagated-prop] (propagated-prop unit-tree prop)
        new-size (compute-translate [(prop :orgpad/unit-width) (prop :orgpad/unit-height)]
                                    (-> parent-view :orgpad/transform :scale)
                                    new-pos old-pos)]
;;    (println id prop translate new-translate old-pos new-pos)
    { :state (cond-> state
               true
                (update-size id (-> unit-tree :unit :db/id) new-size :orgpad/unit-view-child prop)
               (and propagated-prop propagated-unit (info :orgpad/propagate-props-from-children?))
                (update-size (:db/id propagated-prop) (-> propagated-unit :unit :db/id) new-size
                             :orgpad/unit-view-child-propagated prop)) } ))

(defn- child-propagated-props
  [db unit-id child-id props-from-children view-name]
  (let [xform
        (comp
         (mapcat (fn [[type props]]
                   (store/query db
                                (conj propagated-query '[?p :orgpad/type :orgpad/unit-view-child-propagated])
                                [child-id type props])))
         (map (fn [prop-unit]
                (merge (first
                        (store/query db (conj propagated-query
                                              `[~'?p :orgpad/view-name ~(prop-unit :orgpad/view-name)])
                                     [unit-id (prop-unit :orgpad/view-type) [:db/id]]))
                       prop-unit)))
         (filter :db/id))]
    (into [] xform props-from-children)))

(defn- view-units
  [db unit view]
  (store/query db (conj propagated-query '[?p :orgpad/type :orgpad/unit-view])
               [(unit :db/id) (view :orgpad/view-type) [:db/id :orgpad/view-name :orgpad/active-unit]]))

(defn- update-current-active-unit
  [view-units view new-active-unit]
  (let [vid (view :db/id)]
    (map (fn [vu] (if (= (vu :db/id) vid) (assoc vu :orgpad/active-unit new-active-unit) vu)) view-units)))

(defn- update-all-propagated-props
  [db unit props-from-children view-units]
  (let [id (unit :db/id)
        refs (unit :orgpad/refs)]
    (into [] (mapcat (fn [vu]
                       (child-propagated-props db id
                                               (-> refs (get (vu :orgpad/active-unit)) :unit :db/id)
                                               props-from-children (vu :orgpad/view-name))))
                     view-units)))

(defmethod mutate :orgpad.sheet/switch-active
  [{:keys [state]} _ {:keys [unit view direction nof-sheets]}]
  (let [info (registry/get-component-info (view :orgpad/view-type))
        new-active-unit (mod (+ (view :orgpad/active-unit) direction) nof-sheets)
        view-units (-> (view-units state unit view)
                       (update-current-active-unit view new-active-unit))
        update-trans (update-all-propagated-props state
                                                  unit
                                                  (info :orgpad/propagated-props-from-children)
                                                  view-units)]
    { :state
     (store/transact
      state
      (into update-trans
            [[:db/add (view :db/id) :orgpad/active-unit new-active-unit]])) }))
