(ns ^{:doc "Definition of map view parser"}
  orgpad.parsers.map.parser
  (:require [orgpad.core.store :as store]
            [orgpad.effects.core :as eff]
            [orgpad.components.registry :as registry]
            [orgpad.tools.colls :as colls]
            [orgpad.tools.geom :as geom]
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

                       (merge (-> info :orgpad/child-props-default :orgpad.map-view/vertex-props)
                              { :db/id -2
                                :orgpad/refs -1
                                :orgpad/type :orgpad/unit-view-child
                                :orgpad/view-name view-name
                                :orgpad/unit-position pos } )

                       { :db/id -3
                         :orgpad/props-refs -4
                         :orgpad/type :orgpad/unit }

                       (merge (-> info :orgpad/child-props-default :orgpad.map-view/vertex-props)
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
        view' (if id (store/query state [:entity id]) view)
        transform (view' :orgpad/transform)
        new-translate (compute-translate (transform :translate)
                                         (transform :scale)
                                         new-pos old-pos)
        new-transformation (merge transform { :translate new-translate })]
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
        prop' (if id (store/query state [:entity id]) prop)
        new-translate (compute-translate (prop' :orgpad/unit-position)
                                         (-> parent-view :orgpad/transform :scale)
                                         new-pos old-pos)]
    { :state (if (nil? id)
               (store/transact state [(merge prop { :db/id -1
                                                    :orgpad/refs unit-id
                                                    :orgpad/unit-position new-translate
                                                    :orgpad/type :orgpad/unit-view-child })
                                      [:db/add unit-id :orgpad/props-refs -1]])
               (store/transact state [[:db/add id :orgpad/unit-position new-translate]])) } ))

(defn- propagated-prop
  [{:keys [unit view props]} prop parent-view]
  (if (-> unit :orgpad/refs empty? not)
    (let [child-unit (-> unit :orgpad/refs (nth (view :orgpad/active-unit)))
          prop (first
                (filter (fn [p]
                          (and p
                               (= (p :orgpad/view-name) (parent-view :orgpad/view-name))
                               (= (p :orgpad/type) :orgpad/unit-view-child-propagated)))
                       (child-unit :props)))]
      [child-unit prop])
    [nil nil]))

(defn- update-props
  [state id unit-id type prop attrs]
  (if (nil? id)
    (store/transact state [(merge prop { :db/id -1
                                         :orgpad/refs unit-id
                                         :orgpad/type type } attrs)
                           [:db/add unit-id :orgpad/props-refs -1]])
    (store/transact state (into [] (map #(into [:db/add id] %)) attrs))))

(defn- update-propagated-prop
  [{:keys [state]} {:keys [prop parent-view unit-tree] :as payload} comp-val-fn args]
  (let [id (prop :db/id)
        prop' (if id (store/query state [:entity id]) prop)
        info (registry/get-component-info (-> unit-tree :view :orgpad/view-type))
        [propagated-unit propagated-prop] (propagated-prop unit-tree prop parent-view)
        new-val (if comp-val-fn (comp-val-fn payload prop' args) args)]
    { :state (cond-> state
               true
                (update-props id (-> unit-tree :unit :db/id) :orgpad/unit-view-child prop' new-val)
               (and propagated-prop propagated-unit (info :orgpad/propagate-props-from-children?))
                (update-props (:db/id propagated-prop) (-> propagated-unit :unit :db/id)
                              :orgpad/unit-view-child-propagated prop' new-val)) } ))

(defn- comp-new-size
  [{:keys [parent-view old-pos new-pos]} prop' _]
  (let [new-size (compute-translate [(prop' :orgpad/unit-width) (prop' :orgpad/unit-height)]
                                    (-> parent-view :orgpad/transform :scale)
                                    new-pos old-pos)]
    { :orgpad/unit-width (new-size 0)
      :orgpad/unit-height (new-size 1) }))

(defmethod mutate :orgpad.units/map-view-unit-resize
  [env _ payload]
  (update-propagated-prop env payload comp-new-size nil))

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
             (if (view :db/id)
               [[:db/add (view :db/id) :orgpad/active-unit new-active-unit]]
               [[:db/add (unit :db/id) :orgpad/props-refs -1]
                (merge view { :db/id -1
                              :orgpad/type :orgpad/unit-view
                              :orgpad/active-unit new-active-unit })]) )) }))

(defn- find-closest-unit
  [map-unit-tree begin-unit-id position]
  (let [view-name (-> map-unit-tree :view :orgpad/view-name)
        pos (geom/screen->canvas (-> map-unit-tree :view :orgpad/transform) position)
        xform (comp
               (filter (fn [prop]
                         (and prop
                              (= (prop :orgpad/view-name) view-name)
                              (= (prop :orgpad/view-type) :orgpad.map-view/vertex-props)
                              (= (prop :orgpad/type) :orgpad/unit-view-child))))
               (filter (fn [prop]
                         (let [u-pos (prop :orgpad/unit-position)
                               w     (prop :orgpad/unit-width)
                               h     (prop :orgpad/unit-height)]
                           (geom/insideBB [u-pos (geom/++ u-pos [w h])] pos)))))]
    (->> map-unit-tree
         :unit
         :orgpad/refs
         (filter (fn [u] (first (sequence xform (u :props)))))
         first)))

(defmethod mutate :orgpad.units/try-make-new-link-unit
  [{:keys [state]} _ {:keys [map-unit-tree begin-unit-id position]}]
  (let [info (registry/get-component-info :orgpad/map-view)
        closest-unit (find-closest-unit map-unit-tree begin-unit-id position)
        new-state (if closest-unit
                    (store/transact
                     state
                     [{ :db/id -1
                        :orgpad/refs [begin-unit-id (-> closest-unit :unit :db/id)]
                        :orgpad/type :orgpad/unit
                        :orgpad/props-refs -2 }
                      (merge (-> info :orgpad/child-props-default :orgpad.map-view/link-props)
                       { :db/id -2
                         :orgpad/refs -1
                         :orgpad/type :orgpad/unit-view-child
                         :orgpad/view-name (-> map-unit-tree :view :orgpad/view-name)
                        })
                      [:db/add 0 :orgpad/refs -1]
                      ])
                    state)]
    { :state new-state }))

(defmethod mutate :orgpad.units/map-view-link-shape
  [{:keys [state]} _ {:keys [prop parent-view unit-tree pos start-pos end-pos]}]
  (let [id (prop :db/id)
        tr (parent-view :orgpad/transform)
        sp (geom/canvas->screen tr start-pos)
        ep (geom/canvas->screen tr end-pos)
        mid-pt (geom/-- pos (geom/*c (geom/++ sp ep) 0.5))]
    { :state (store/transact state [[:db/add id :orgpad/link-mid-pt mid-pt]]) }))

(defmethod mutate :orgpad.units/map-view-unit-border-color
  [env _ {:keys [color] :as payload}]
  (update-propagated-prop env payload nil { :orgpad/unit-border-color color }))

(defmethod mutate :orgpad.units/map-view-unit-bg-color
  [env _ {:keys [color] :as payload}]
  (update-propagated-prop env payload nil { :orgpad/unit-bg-color color }))

(defmethod mutate :orgpad.units/map-view-unit-border-width
  [env _ {:keys [orgpad/unit-border-width] :as payload}]
  (update-propagated-prop env payload nil { :orgpad/unit-border-width unit-border-width }))

(defmethod mutate :orgpad.units/map-view-unit-border-radius
  [env _ {:keys [orgpad/unit-corner-x orgpad/unit-corner-y] :as payload}]
  (update-propagated-prop env payload nil (cond-> {}
                                            unit-corner-x (assoc :orgpad/unit-corner-x unit-corner-x)
                                            unit-corner-y (assoc :orgpad/unit-corner-y unit-corner-y) )))
