(ns ^{:doc "Definition of map view parser"}
  orgpad.parsers.map.parser
  (:require [com.rpl.specter :refer [keypath]]
            [orgpad.core.store :as store]
            [orgpad.effects.core :as eff]
            [orgpad.components.registry :as registry]
            [orgpad.tools.colls :as colls]
            [orgpad.tools.geom :as geom :refer [-- ++ *c screen->canvas canvas->screen]]
            [orgpad.tools.orgpad :as ot]
            [orgpad.tools.orgpad-db :as otdb]
            [orgpad.tools.order-numbers :as ordn]
            [orgpad.tools.geocache :as geocache]
            [orgpad.tools.dscript :as ds]
            [orgpad.tools.jcolls :as jcolls]
            [orgpad.tools.dom :as dom]
            [orgpad.tools.bezier :as bez]
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
                       {:db/id (vswap! indexer dec)
                        :orgpad/type :orgpad/unit-view-child-propagated
                        :orgpad/refs -1}))))]
    (into [] props-units-transducer props-from-children)))

(defmethod mutate :orgpad.units/new-sheet
  [{:keys [state]} _ {:keys [unit view params]}]
  (let [unit-id (unit :db/id)
        info (registry/get-component-info (view :orgpad/view-type))
        propagated-refs (prepare-propagated-props
                         state unit-id
                         (info :orgpad/propagated-props-from-children))
        t (colls/minto
           [{:db/id -1
             :orgpad/type :orgpad/unit
             :orgpad/props-refs (into [] (map :db/id) propagated-refs)}
            (if (-> :db/id view nil?)
              (merge view
                     {:db/id -2
                      :orgpad/type :orgpad/unit-view
                      :orgpad/refs unit-id
                      :orgpad/active-unit (count (unit :orgpad/refs))})
              [:db/add (view :db/id) :orgpad/active-unit (count (unit :orgpad/refs))])
            [:db/add unit-id :orgpad/refs -1]]
           propagated-refs
           (if (-> :db/id view nil?) [[:db/add unit-id :orgpad/props-refs -2]] []))]
    {:state (store/transact state t)}))

(defmethod mutate :orgpad.units/new-pair-unit
  [{:keys [state global-cache]} _ {:keys [parent position transform view-name style]}]
  (let [info (registry/get-component-info :orgpad/map-view)
        default-props (-> info
                          :orgpad/child-props-default
                          :orgpad.map-view/vertex-props
                          (as-> x
                                (if style
                                  (assoc x :orgpad/view-style (:orgpad/style-name style))
                                  x)))
        {:keys [translate scale]} transform
        pos  [(/ (- (:center-x position) (translate 0)) scale)
              (/ (- (:center-y position) (translate 1)) scale)]
        new-state
        (-> state
            (store/with-history-mode {:new-record true
                                      :mode :acc})
            (store/transact
             [{:db/id -1
               :orgpad/type :orgpad/unit
               :orgpad/props-refs (if style [-2 (:db/id style)] -2)
               :orgpad/refs -3}

              (merge default-props
                     {:db/id -2
                      :orgpad/refs -1
                      :orgpad/type :orgpad/unit-view-child
                      :orgpad/view-name view-name
                      :orgpad/unit-position pos
                      :orgpad/context-unit parent
                      :orgpad/unit-visibility true})

              {:db/id -3
               :orgpad/props-refs -4
               :orgpad/type :orgpad/unit}

              (merge default-props
                     {:db/id -4
                      :orgpad/refs -3
                      :orgpad/type :orgpad/unit-view-child-propagated
                      :orgpad/view-name view-name})

              [:db/add parent :orgpad/refs -1]])

            (as-> s (store/transact s [[:app-state :selections (keypath parent)] #{(-> s store/tempids (get -1))}]))
            (store/with-history-mode :add))
        w (if style (:orgpad/unit-width style) (:orgpad/unit-width default-props))
        h (if style (:orgpad/unit-height style) (:orgpad/unit-height default-props))]
    (geocache/create! global-cache parent view-name)
    (geocache/update-box! global-cache parent view-name
                          (get (store/tempids new-state) -1) (-- pos [(/ w 2) (/ h 2)])
                          (geom/ensure-size [w h]))
    {:state new-state}))

(defn- compute-translate
  [translate scale new-pos old-pos & [mult]]
  (let [m (or mult 1)]
    [(+ (translate 0) (/ (* m (- (new-pos 0) (old-pos 0))) scale))
     (+ (translate 1) (/ (* m (- (new-pos 1) (old-pos 1))) scale))]))

(defmethod mutate :orgpad.units/map-view-canvas-move
  [{:keys [state]} _ {:keys [view unit-id old-pos new-pos]}]
  (let [id (view :db/id)
        view' (if id (store/query state [:entity id]) view)
        transform (view' :orgpad/transform)
        new-translate (compute-translate (transform :translate)
                                         1 ;; (transform :scale)
                                         new-pos old-pos)
        new-transformation (merge transform {:translate new-translate})]
    {:state (if (nil? id)
              (store/transact state [(merge view {:db/id -1
                                                  :orgpad/refs unit-id
                                                  :orgpad/transform new-transformation
                                                  :orgpad/type :orgpad/unit-view})
                                     [:db/add unit-id :orgpad/props-refs -1]])
              (store/transact state [[:db/add id :orgpad/transform new-transformation]]))}))

(defmethod mutate :orgpad.units/map-view-unit-move
  [{:keys [state global-cache]} _ {:keys [prop parent-view unit-tree old-pos new-pos]}]
  (let [id (prop :db/id)
        unit-id (ot/uid unit-tree)
        prop' (ot/get-prop-from-db-styles state (:props unit-tree) prop :orgpad.map-view/vertex-props-style) ;;(if id (store/query state [:entity id]) prop)
        new-translate (compute-translate (prop' :orgpad/unit-position)
                                         (-> parent-view :orgpad/transform :scale)
                                         new-pos old-pos)
        new-state
        (if (nil? id)
          (store/transact state [(merge prop {:db/id -1
                                              :orgpad/refs unit-id
                                              :orgpad/unit-position new-translate
                                              :orgpad/type :orgpad/unit-view-child})
                                 [:db/add unit-id :orgpad/props-refs -1]])
          (store/transact state [[:db/add id :orgpad/unit-position new-translate]]))
        size (geom/ensure-size [(prop' :orgpad/unit-width) (prop' :orgpad/unit-height)])
        size2 (*c size 0.5)]
    (geocache/update-box! global-cache (ot/pid parent-view) (:orgpad/view-name parent-view)
                          unit-id (-- new-translate size2) size
                          (-- (prop' :orgpad/unit-position) size2) size)
    ;; (js/console.log "moving unit to" unit-id new-translate size " - " (prop' :orgpad/unit-position) size)
    {:state new-state}))

(defn- propagated-prop
  [{:keys [unit]} prop view]
  ;; FIXME: update of propageted props should be done when switching view types
  ;; when we have mix of units with vertex props and units without it is not clear what to do
  (if (and (-> unit :orgpad/refs empty? not) view (contains? view :orgpad/active-unit)
           (< (:orgpad/active-unit view) (count (:orgpad/refs unit))))
    (let [child-unit (-> unit ot/sort-refs (nth (view :orgpad/active-unit)))
          prop (first
                (filter (fn [p]
                          (and p
                               (= (p :orgpad/view-type) (prop :orgpad/view-type))
                               (= (p :orgpad/view-name) (prop :orgpad/view-name))
                               (= (p :orgpad/type) :orgpad/unit-view-child-propagated)))
                        (-> child-unit :unit :orgpad/props-refs)))]
      [child-unit prop])
    [nil nil]))

(defn- update-props
  [state id unit-id type prop attrs]
  (if (nil? id)
    (store/transact state [(merge prop {:db/id -1
                                        :orgpad/refs unit-id
                                        :orgpad/type type} attrs)
                           [:db/add unit-id :orgpad/props-refs -1]])
    (store/transact state (into [] (map #(into [:db/add id] %)) attrs))))

(defn- sheet-view-unit
  [db {:keys [unit view]}]
  (if (contains? view :orgpad/active-unit)
    view
    (let [view-id (store/query db '[:find ?u .
                                    :in $ ?p ?n
                                    :where
                                    [?u :orgpad/refs ?p]
                                    [?u :orgpad/type :orgpad/unit-view]
                                    [?u :orgpad/view-type :orgpad/map-tuple-view]
                                    [?u :orgpad/view-name ?n]]
                               [(unit :db/id) (view :orgpad/view-name)])]
      (if view-id
        (store/query db [:entity view-id])
        {:orgpad/active-unit 0}))))

(defn- update-propagated-prop
  [{:keys [state] :as env} {:keys [prop unit-tree] :as payload} comp-val-fn args & [global-update-fn]]
  (let [id (prop :db/id)
        prop' (ot/get-prop-from-db-styles state (:props unit-tree) prop :orgpad.map-view/vertex-props-style) ;;(if id (store/query state [:entity id]) prop)
        ;; info (registry/get-component-info (-> unit-tree :view :orgpad/view-type))
        sheet-view
        (sheet-view-unit state unit-tree)
        [propagated-unit propagated-prop] (propagated-prop unit-tree prop' sheet-view)
        new-val (if comp-val-fn (comp-val-fn payload prop' args) args)]
    (when global-update-fn
      (global-update-fn env payload prop' new-val))
    {:state (cond-> state
              true
              (update-props id (ot/uid unit-tree) :orgpad/unit-view-child prop' new-val)
              (and propagated-prop propagated-unit)
              (update-props (:db/id propagated-prop) (ot/uid propagated-unit)
                            :orgpad/unit-view-child-propagated prop' new-val))}))

(def ^:private MIN-SIZE 5)

(defn- comp-new-size
  [{:keys [parent-view old-pos new-pos]} prop' {:keys [global-cache]}]
  (let [new-size (->
                  (compute-translate [(prop' :orgpad/unit-width) (prop' :orgpad/unit-height)]
                                     (-> parent-view :orgpad/transform :scale)
                                     new-pos old-pos 2)
                  (as-> x [(max MIN-SIZE (x 0))
                           (max MIN-SIZE (x 1))]))]
    ;; (js/console.log "Resize" new-size)
    {:orgpad/unit-width (new-size 0)
     :orgpad/unit-height (new-size 1)}))

(defn- update-geocache-after-resize
  [{:keys [global-cache]} {:keys [parent-view unit-tree]} prop size]
  ;; (js/console.log "update-geocache-after-resize" prop size)
  (let [new-size (geom/ensure-size [(:orgpad/unit-width size) (:orgpad/unit-height size)])
        old-size (geom/ensure-size [(:orgpad/unit-width prop) (:orgpad/unit-height prop)])]
    (geocache/update-box! global-cache (ot/pid parent-view) (:orgpad/view-name parent-view)
                          (ot/uid unit-tree) (ot/left-top (:orgpad/unit-position prop) new-size)
                          new-size
                          (ot/left-top (:orgpad/unit-position prop) old-size)
                          old-size)))

(defmethod mutate :orgpad.units/map-view-unit-resize
  [env _ payload]
  (update-propagated-prop env payload comp-new-size nil update-geocache-after-resize))

(defn- comp-new-size1
  [{:keys [orgpad/unit-width orgpad/unit-height]} prop' {:keys [global-cache]}]
  (let [new-size  (->
                   [(or unit-width (prop' :orgpad/unit-width))
                    (or unit-height (prop' :orgpad/unit-height))]
                   (as-> x [(max MIN-SIZE (x 0))
                            (max MIN-SIZE (x 1))]))]
    {:orgpad/unit-width (new-size 0)
     :orgpad/unit-height (new-size 1)}))

(defmethod mutate :orgpad.units/map-view-unit-set-size
  [env _ payload]
  (update-propagated-prop env payload comp-new-size1 nil update-geocache-after-resize))

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
  (let [vs (store/query db (conj propagated-query '[?p :orgpad/type :orgpad/unit-view])
                        [(unit :db/id) (view :orgpad/view-type) [:db/id :orgpad/view-name :orgpad/active-unit]])]
    (if (empty? vs) [view] vs)))

(defn- update-current-active-unit
  [view-units view new-active-unit]
  (let [vid (view :db/id)]
    (map (fn [vu] (if (= (vu :db/id) vid) (assoc vu :orgpad/active-unit new-active-unit) vu)) view-units)))

(defn- update-all-propagated-props
  [db unit props-from-children view-units]
  (let [id (unit :db/id)
        refs (ot/sort-refs unit)]
    (into [] (comp
              (mapcat (fn [vu]
                        (child-propagated-props db id
                                                (-> refs (get (vu :orgpad/active-unit)) ot/uid)
                                                props-from-children (vu :orgpad/view-name))))
              (mapcat (fn [prop]
                        (if (contains? props-from-children (:orgpad/view-type prop))
                          (concat [prop] (->> props-from-children
                                              ((:orgpad/view-type prop))
                                              (filter #(->> % (contains? prop) not))
                                              (map (fn [prop-name]
                                                     [:db.fn/retractAttribute (:db/id prop) prop-name]))))
                          [prop]))))
          view-units)))

(defn- update-geocache-after-switch-active
  [state global-cache unit-tree update-trans]
  (let [view-path (-> unit-tree :path-info :orgpad/view-path)
        n (-> view-path count dec)]
    (when (and (>= n 1)
               (geocache/has-geocache? global-cache (nth view-path (dec n)) (nth view-path n)))
      (let [parent-id (nth view-path (dec n))
            parent-name (nth view-path n)]
        (when-let [prop (-> (filter #(= (:orgpad/view-name %) parent-name) update-trans) first)]
          (let [e (ot/get-prop-from-db-styles state (:props unit-tree) prop :orgpad.map-view/vertex-props-style) ;;(store/query state [:entity (prop :db/id)])
                prop' (merge (ot/get-style-from-db state
                                                   :orgpad.map-view/vertex-props-style
                                                   (:orgpad/view-style prop))
                             prop)
                new-size (geom/ensure-size [(prop' :orgpad/unit-width) (prop' :orgpad/unit-height)])
                old-size (geom/ensure-size [(e :orgpad/unit-width) (e :orgpad/unit-height)])]
            (geocache/update-box! global-cache parent-id parent-name
                                  (ot/uid unit-tree) (ot/left-top (e :orgpad/unit-position) new-size)
                                  new-size
                                  (ot/left-top (e :orgpad/unit-position) old-size)
                                  old-size)))))))

(defn- switch-active
  [{:keys [state global-cache]}
   {:keys [unit-tree direction nof-sheets new-active-pos]}]
  (let [{:keys [unit view]} unit-tree
        info (registry/get-component-info (view :orgpad/view-type))
        new-active-unit (mod (+ (view :orgpad/active-unit) direction) nof-sheets)
        view-units (-> (view-units state unit view)
                       (update-current-active-unit view new-active-unit))
        update-trans (update-all-propagated-props state
                                                  unit
                                                  (info :orgpad/propagated-props-from-children)
                                                  view-units)]
    (update-geocache-after-switch-active state global-cache unit-tree update-trans)
    (into update-trans
          (if (view :db/id)
            [[:db/add (view :db/id) :orgpad/active-unit
              (or new-active-pos new-active-unit)]]
            [[:db/add (unit :db/id) :orgpad/props-refs -1]
             (merge view {:db/id -1
                          :orgpad/type :orgpad/unit-view
                          :orgpad/active-unit (or new-active-pos
                                                  new-active-unit)})]))))

(defmethod mutate :orgpad.sheet/switch-active
  [env _ params]
  (let [qry (switch-active env params)]
    {:state
     (store/transact (:state env) qry)}))

(defn- vertex-props-pred
  [view-name]
  (partial ot/props-pred-no-ctx view-name :orgpad.map-view/vertex-props :orgpad/unit-view-child))

(defn- inside?
  [map-unit-tree pos u]
  (let [view-name (ot/view-name map-unit-tree)]
    (when-let [prop (ot/get-props-view-child-styled (:props u) view-name (ot/uid map-unit-tree)
                                                    :orgpad.map-view/vertex-props
                                                    :orgpad.map-view/vertex-props-style
                                                    :orgpad/map-view)]
      (let [u-pos (prop :orgpad/unit-position)
            d     [(/ (prop :orgpad/unit-width) 2)
                   (/ (prop :orgpad/unit-height) 2)]]
        (geom/insideBB [(-- u-pos d) (++ u-pos d)] pos)))))

(defn- find-closest-unit
  [map-unit-tree begin-unit-id position]
  (let [pos (screen->canvas (-> map-unit-tree :view :orgpad/transform) position)]
    (->> map-unit-tree
         :unit
         :orgpad/refs
         (filter #(inside? map-unit-tree pos %))
         first)))

(defn- update-geocache-after-new-link
  [state global-cache parent-id uid view-name begin-unit-id closest-unit mid-pt cyclic?]
  (let [bu (store/query state [:entity begin-unit-id])
        pred (vertex-props-pred view-name)
        prop1 (ds/find-props bu pred)
        prop2 (ds/find-props-base (:unit closest-unit) pred)
        bbox (geom/link-bbox (prop1 :orgpad/unit-position) (prop2 :orgpad/unit-position) mid-pt)
        pos (bbox 0)
        size (geom/ensure-size (-- (bbox 1) (bbox 0)))]
    (jcolls/aset! global-cache uid "link-info" view-name [pos size])
    (geocache/update-box! global-cache parent-id view-name
                          uid
                          (if cyclic?
                            (ot/left-top pos [(:orgpad/unit-width prop1) (:orgpad/unit-height prop1)])
                            pos)
                          size nil nil
                          #js [begin-unit-id (ot/uid closest-unit)])))

(defmethod mutate :orgpad.units/try-make-new-link-unit
  [{:keys [state global-cache]} _ {:keys [map-unit-tree begin-unit-id position style]}]
  (let [info (registry/get-component-info :orgpad/map-view)
        closest-unit (find-closest-unit map-unit-tree begin-unit-id position)
        parent-id (ot/uid map-unit-tree)
        cyclic? (= begin-unit-id (ot/uid closest-unit))
        mid-pt-rel (if style
                     (if (and cyclic? (= (:orgpad/link-mid-pt style) [0 0]))
                       [-40 0]
                       (:orgpad/link-mid-pt style))
                     (if cyclic? [-40 0] [0 0]))
        default-prop (-> info :orgpad/child-props-default :orgpad.map-view/link-props
                         (as-> x (if style (assoc x :orgpad/view-style (:orgpad/style-name style)) x)))
        new-state (if closest-unit
                    (store/transact
                     state
                     [{:db/id -1
                       :orgpad/refs [begin-unit-id (ot/uid closest-unit)]
                       :orgpad/refs-order (sorted-set-by colls/first-<
                                                         [ordn/canonical-zero begin-unit-id]
                                                         [(ordn/canonical-next ordn/canonical-zero)
                                                          (ot/uid closest-unit)])
                       :orgpad/type :orgpad/unit
                       :orgpad/props-refs (if style [-2 (:db/id style)] -2)}
                      (merge default-prop
                             {:db/id -2
                              :orgpad/refs -1
                              :orgpad/type :orgpad/unit-view-child
                              :orgpad/view-name (-> map-unit-tree :view :orgpad/view-name)
                              :orgpad/context-unit parent-id}
                             (when (or (nil? style) cyclic?)
                               {:orgpad/link-mid-pt mid-pt-rel}))
                      [:db/add parent-id :orgpad/refs -1]])
                    state)]
    (when closest-unit
      (update-geocache-after-new-link state global-cache (ot/uid map-unit-tree)
                                      (get (store/tempids new-state) -1)
                                      (ot/view-name map-unit-tree)
                                      begin-unit-id closest-unit mid-pt-rel cyclic?))
    {:state new-state}))

(defmethod mutate :orgpad.units/map-view-link-shape
  [{:keys [state]} _ {:keys [prop parent-view unit-tree pos start-pos end-pos mid-pt t cyclic? start-size]}]
  (let [id (prop :db/id)
        tr (parent-view :orgpad/transform)
        pos-tr (screen->canvas tr pos)
        start-pos' (if cyclic? (ot/left-top start-pos start-size) start-pos)
        end-pos' (if cyclic? (ot/left-top end-pos start-size) end-pos)
        mid-pt'' (if cyclic? (ot/left-top mid-pt start-size) mid-pt)
        pos' (bez/get-point-on-quadratic-curve start-pos' pos-tr end-pos' t 0.5)
        old-mid-pt (bez/get-point-on-quadratic-curve start-pos' mid-pt'' end-pos' t 0.5)
        mid-pt' (-- pos' (*c (++ start-pos' end-pos') 0.5))
        vprop (ot/get-props-view-child (:props unit-tree) (:orgpad/view-name parent-view)
                                       (-> parent-view :orgpad/refs first :db/id)
                                       :orgpad.map-view/vertex-props)]
    {:state (store/transact state (into [[:db/add id :orgpad/link-mid-pt mid-pt']]
                                        (when (-> vprop nil? not)
                                          [[:db/add (:db/id vprop) :orgpad/unit-position
                                            (++ (:orgpad/unit-position vprop)
                                                (-- pos' old-mid-pt))]])))}))

(defmethod mutate :orgpad.units/map-view-unit-border-color
  [env _ {:keys [color] :as payload}]
  (update-propagated-prop env payload nil {:orgpad/unit-border-color color}))

(defmethod mutate :orgpad.units/map-view-unit-bg-color
  [env _ {:keys [color] :as payload}]
  (update-propagated-prop env payload nil {:orgpad/unit-bg-color color}))

(defmethod mutate :orgpad.units/map-view-unit-border-width
  [env _ {:keys [orgpad/unit-border-width] :as payload}]
  (update-propagated-prop env payload nil {:orgpad/unit-border-width unit-border-width}))

(defmethod mutate :orgpad.units/map-view-unit-border-radius
  [env _ {:keys [orgpad/unit-corner-x orgpad/unit-corner-y] :as payload}]
  (update-propagated-prop env payload nil (cond-> {}
                                            unit-corner-x (assoc :orgpad/unit-corner-x unit-corner-x)
                                            unit-corner-y (assoc :orgpad/unit-corner-y unit-corner-y))))

(defmethod mutate :orgpad.units/map-view-unit-border-style
  [env _ {:keys [orgpad/unit-border-style] :as payload}]
  (update-propagated-prop env payload nil {:orgpad/unit-border-style unit-border-style}))

(defmethod mutate :orgpad.units/map-view-unit-padding
  [env _ {:keys [orgpad/unit-padding] :as payload}]
  (update-propagated-prop env payload nil {:orgpad/unit-padding unit-padding}))

(defn- get-auto-size
  [state {:keys [unit-tree]} new-style]
  (let [size
        (case (-> unit-tree :view :orgpad/view-type)
          :orgpad/atomic-view
          (-> unit-tree :view :orgpad/atom dom/get-html-size)

          :orgpad/map-tuple-view
          (let [sheet-view (sheet-view-unit state unit-tree)
                child-unit (-> unit-tree :unit ot/sort-refs (nth (:orgpad/active-unit sheet-view)))]
            (some->> child-unit
                     :unit
                     :orgpad/props-refs
                     (filter (partial ot/props-pred-no-ctx (:orgpad/view-name sheet-view)
                                      :orgpad/atomic-view
                                      :orgpad/unit-view))
                     first
                     :orgpad/atom
                     dom/get-html-size
                     geom/ensure-width
                     (otdb/update-size new-style)))

          nil)]
    (when size
      {:orgpad/unit-width (size 0)
       :orgpad/unit-height (size 1)})))

(defmethod mutate :orgpad.units/map-view-unit-style
  [{:keys [state] :as env} _ {:keys [orgpad/view-style prop] :as payload}]
  (let [old-style (ot/get-style-from-db state :orgpad.map-view/vertex-props-style (:orgpad/view-style prop))
        new-style (ot/get-style-from-db state :orgpad.map-view/vertex-props-style view-style)
        uid (ot/pid prop)
        size (when (and (-> old-style :orgpad/unit-autoresize? not)
                        (:orgpad/unit-autoresize? new-style))
               (get-auto-size state payload new-style))]
    (->
     env
     (update :state #(store/with-history-mode % {:new-record true :mode :acc}))
     (update-propagated-prop payload nil (merge {:orgpad/view-style view-style} size))
     :state
     (as-> state'
           (let [unit-styles (ot/get-all-unit-styles-names state' uid)]
             (store/transact state' (into [[:db/add uid :orgpad/props-refs (:db/id new-style)]]
                                          (when (->> old-style :orgpad/style-name (contains? unit-styles) not)
                                            [[:db/retract uid :orgpad/props-refs (:db/id old-style)]])))))
     (store/with-history-mode :add)
     (->> (assoc {} :state)))))

;; 'not' and 'or' is not properly supported in ds query yet
(def ^:private parents-props-query
  '[:find ?parent ?t
    :in $ ?e
    :where
    [?parent :orgpad/refs ?e]
    [?parent :orgpad/type ?t]])

(defn- find-relative
  [db id qry]
  (store/query db qry [id]))

(defn- find-parents-or-props
  [db id pred]
  (into []
        (comp
         (filter pred)
         (map first))
        (find-relative db id parents-props-query)))

(defn- parent?
  [[_ type]]
  (or (= type :orgpad/unit)
      (= type :orgpad/root-unit)))

(defn- find-parents
  [db id]
  (find-parents-or-props db id parent?))

(defn- prop-but-style
  [db [id type :as params]]
  (and (-> params parent? not)
       (-> db (store/query '[:find ?i .
                             :in $ ?uid
                             :where
                             [?uid :orgpad/independent ?i]] [id])
           nil?)))

(defn- find-props
  [db id]
  (find-parents-or-props db id (partial prop-but-style db)))

(def ^:private child-query
  '[:find [?child ...]
    :in $ ?e
    :where
    [?e :orgpad/refs ?child]])

(def ^:private edge-query
  '[:find [?edge ...]
    :in $ ?e
    :where
    [?parent :orgpad/refs ?e]
    [?parent :orgpad/refs ?edge]
    [?edge :orgpad/refs ?e]])

(def ^:private edge-check-query
  '[:find ?edge ?ro
    :in $ ?edge
    :where
    [?edge :orgpad/refs-order ?ro]])

(defn- find-children-deep
  [db ids]
  (mapcat (fn [id]
            (concat [id]
                    (find-props db id)
                    (find-children-deep db (find-relative db id child-query)))) ids))

(defn- find-refs-orders
  [db id]
  (store/query db
               '[:find ?parent ?o
                 :in $ ?e
                 :where
                 [?parent :orgpad/refs ?e]
                 [?parent :orgpad/refs-order ?o]]
               [id]))

(defn- remove-from-refs-orders
  [id refs-orders]
  (map (fn [[eid o]]
         [eid (->> o
                   (drop-while (fn [[_ eid]] (= id eid)))
                   first
                   (disj o))])
       refs-orders))

(defn- remove-from-refs-orders-qry
  [db id]
  (let [refs-orders (->> id (find-refs-orders db) (remove-from-refs-orders id))]
    (map (fn [[pid o]] [:db/add pid :orgpad/refs-order o]) refs-orders)))

(defn- update-geocache-after-remove
  [global-cache parents id other]
  (let [ids (apply array (conj other id))]
    (doseq [uid ids]
      (js-delete global-cache uid))
    (doseq [pid parents]
      (geocache/clear! global-cache pid ids))))

(defn- edge-label
  [db {:keys [id view-name ctx-unit]}]
  (-> db
      (store/query '[:find (pull ?v [*]) .
                     :in $ ?uid ?view-name ctx-unit
                     :where
                     [?uid :orgpad/props-refs ?v]
                     [?v :orgpad/view-type :orgpad.map-view/vertex-props]
                     [?v :orgpad/view-name ?view-name]
                     [?v :orgpad/context-unit ?ctx-unit]
                     [?uid :orgpad/props-refs ?l]
                     [?l :orgpad/view-type :orgpad.map-view/link-props]
                     [?l :orgpad/view-name ?view-name]
                     [?l :orgpad/context-unit ?ctx-unit]] [id view-name ctx-unit])))

(defn- atomic-view
  [db {:keys [id view-name]}]
  (store/query db '[:find (pull ?a [*]) .
                    :in $ ?uid ?view-name
                    :where
                    [?uid :orgpad/props-refs ?a]
                    [?a :orgpad/view-type :orgpad/atomic-view]
                    [?a :orgpad/type :orgpad/unit-view]
                    [?a :orgpad/view-name ?view-name]] [id view-name]))

(defn- remove-unit
  [{:keys [state global-cache force-update!]} {:keys [id ctx-unit view-name] :as params}]
  (let [v (edge-label state params)]
    (if v
      (let [a (atomic-view state params)]
        ;; (force-update!)
        [[:db/retract id :orgpad/props-refs (:db/id v)]
         [:db/retract id :orgpad/props-refs (:db/id a)]
         [:db.fn/retractEntity (:db/id v)]
         [:db.fn/retractEntity (:db/id a)]])
      (let [units-to-remove (find-children-deep state [id])
            parents (find-parents state id)
            edges (mapcat #(find-relative state % edge-check-query) (find-relative state id edge-query))
            edges-to-remove (into [] (comp (filter (fn [[_ ro]] (= (count ro) 2))) (map first)) edges)
            edges-props-to-remove (mapcat (fn [eid] (find-props state eid)) edges-to-remove)
            final-qry (colls/minto []
                                   (map (fn [pid] [:db/retract pid :orgpad/refs id]) parents)
                                   (remove-from-refs-orders-qry state id)
                                   (map (fn [eid] [:db.fn/retractEntity eid])
                                        (concat units-to-remove edges-to-remove edges-props-to-remove)))]
        (update-geocache-after-remove global-cache parents id edges-to-remove)
        final-qry))))

(defmethod mutate :orgpad.units/remove-unit
  [env _ params]
  (let [final-qry (remove-unit env params)
        new-state (store/transact (:state env) final-qry)]
    {:state  new-state}))

(defn- update-link-props
  [{:keys [state]} {:keys [prop parent-view unit-tree]} val]
  (let [id (prop :db/id)
        prop' (if id (store/query state [:entity id]) prop)]
    {:state (update-props state id (ot/uid unit-tree) :orgpad/unit-view-child prop' val)}))

(defmethod mutate :orgpad.units/map-view-link-color
  [env _ {:keys [color] :as payload}]
  (update-link-props env payload {:orgpad/link-color color}))

(defmethod mutate :orgpad.units/map-view-line-width
  [env _ {:keys [orgpad/link-width] :as payload}]
  (update-link-props env payload {:orgpad/link-width link-width}))

(defmethod mutate :orgpad.units/map-view-link-style
  [{:keys [state]} _ {:keys [prop parent-view unit-tree orgpad/link-style-1 orgpad/link-style-2]}]
  (let [id (prop :db/id)
        prop' (if id
                (ot/get-prop-from-db-styles state (:props unit-tree) prop :orgpad.map-view/link-props-style) ;;(store/query state [:entity id])
                (-> prop (dissoc :orgpad/link-style-2) (dissoc :orgpad/link-style-1)))
        val (aclone (prop' :orgpad/link-dash))]
    (if link-style-1
      (aset val 0 link-style-1)
      (aset val 1 link-style-2))
    {:state (update-props state id (ot/uid unit-tree) :orgpad/unit-view-child prop' {:orgpad/link-dash val})}))

(defmethod mutate :orgpad.units/map-view-style-link
  [{:keys [state] :as env} _ {:keys [:orgpad/view-style prop] :as payload}]
  (let [old-style (ot/get-style-from-db state :orgpad.map-view/link-props-style (:orgpad/view-style prop))
        new-style (ot/get-style-from-db state :orgpad.map-view/link-props-style view-style)
        uid (ot/pid prop)]
    (->
     env
     (update :state #(store/with-history-mode % {:new-record true :mode :acc}))
     (update-link-props payload {:orgpad/view-style view-style})
     :state
     (store/transact [[:db/retract uid :orgpad/props-refs (:db/id old-style)]
                      [:db/add uid :orgpad/props-refs (:db/id new-style)]])
     (store/with-history-mode :add)
     (->> (assoc {} :state)))))

(defmethod mutate :orgpad.units/map-view-link-arrow-pos
  [env _ {:keys [orgpad/link-arrow-pos] :as payload}]
  (update-link-props env payload {:orgpad/link-arrow-pos link-arrow-pos}))

(defmethod mutate :orgpad.units/map-view-link-type
  [env _ {:keys [orgpad/link-type] :as payload}]
  (update-link-props env payload {:orgpad/link-type link-type}))

;; TODO - remove children that are not siblings (edge is strict parent and not connect them)
(defmethod mutate :orgpad.units/map-view-link-remove
  [{:keys [state global-cache]} _ id]
  (let [parents (find-parents state id)
        final-qry (colls/minto [[:db.fn/retractEntity id]]
                               (map (fn [eid] [:db.fn/retractEntity eid]) (find-props state id))
                               (remove-from-refs-orders-qry state id)
                               (map (fn [pid] [:db/retract pid :orgpad/refs id]) parents))]
    (update-geocache-after-remove global-cache parents id [])
    {:state (store/transact state final-qry)}))

(defmethod mutate :orgpad.units/remove-active-sheet-unit
  [env _ {:keys [unit view] :as unit-tree}]
  (let [nof-sheets (ot/refs-count unit-tree)]
    (if (= nof-sheets 1)
      {:state (:state env)}
      (let [active-idx (view :orgpad/active-unit)
            ruid (-> unit (ot/get-sorted-ref active-idx) ot/uid)
            last? (= active-idx (dec nof-sheets))
            active-pos (if last? (- nof-sheets 2) active-idx)
            switch-qry (switch-active env {:unit-tree unit-tree
                                           :direction (if last? -1 1)
                                           :nof-sheets nof-sheets
                                           :new-active-pos active-pos})
            remove-qry (remove-unit env {:id ruid})
            final-qry (into switch-qry remove-qry)]
        {:state (store/transact (:state env) final-qry)}))))

(defn- repeat-action
  [{:keys [state] :as env} selection props action make-args]
  (let [new-state (reduce (fn [s [uid prop]]
                            (:state (mutate (assoc env :state s) action
                                            (make-args uid prop))))
                          (store/with-history-mode state {:new-record true
                                                          :mode :acc})
                          (map vector selection props))]
    {:state (store/with-history-mode new-state :add)}))

(defmethod mutate :orgpad.units/map-view-repeat-action
  [env _ {:keys [unit-tree selection action old-pos new-pos] :as args}]
  (repeat-action env selection (ot/child-vertex-props identity unit-tree selection)
                 action
                 (fn [uid prop]
                   {:prop prop
                    :parent-view (:view unit-tree)
                    :unit-tree {:unit {:db/id uid}}
                    :old-pos old-pos
                    :new-pos new-pos})))

(defmethod mutate :orgpad.units/map-view-select-units-by-bb
  [{:keys [state]} _ {:keys [unit-tree bb]}]
  (let [bbs (ot/child-bbs unit-tree)
        selected (into #{} (comp
                            (filter #(geom/bbs-intersect? bb (:bb %)))
                            (map :id)) bbs)]
    {:state (store/transact state [[:app-state :selections (-> unit-tree ot/uid keypath)] selected])}))

(defmethod mutate :orgpad.units/remove-units
  [env _ [{:keys [pid view-name]} selection]]
  (let [res (repeat-action env selection (repeat nil) :orgpad.units/remove-unit
                           (fn [uid _]
                             {:id uid
                              :view-name view-name
                              :ctx-unit pid}))]
    {:state (store/transact (:state res) [[:app-state :selections (keypath pid)] nil])}))

(defmethod mutate :orgpad.units/try-make-new-links-unit
  [env _ {:keys [unit-tree selection position style]}]
  (repeat-action env selection (repeat nil) :orgpad.units/try-make-new-link-unit
                 (fn [uid _]
                   {:map-unit-tree unit-tree
                    :begin-unit-id uid
                    :position position
                    :style style})))

(defmethod mutate :orgpad.units/map-view-units-change-props
  [env _ {:keys [unit-tree selection action prop-name prop-val]}]
  (repeat-action env selection (ot/child-vertex-props identity unit-tree selection) action
                 (fn [uid prop]
                   {:prop prop
                    :parent-view (:view unit-tree)
                    :unit-tree (ot/get-ref-by-uid unit-tree uid)
                    prop-name prop-val})))

(defn- nop [])

(defmethod mutate :orgpad.units/make-lnk-vtx-prop
  [{:keys [state] :as env} _ {:keys [unit-tree context-unit pos view-name style]}]
  (let [info (registry/get-component-info :orgpad/map-view)
        default-props (-> info
                          :orgpad/child-props-default
                          :orgpad.map-view/vertex-props
                          (as-> x
                                (if style
                                  (assoc x :orgpad/view-style (:orgpad/style-name style))
                                  x)))
        new-state
        (-> state
            (store/with-history-mode {:new-record true
                                      :mode :acc})
            (store/transact
             [(merge default-props
                     {:db/id -1
                      :orgpad/refs (ot/uid unit-tree)
                      :orgpad/type :orgpad/unit-view-child
                      :orgpad/view-name view-name
                      :orgpad/unit-position pos
                      :orgpad/context-unit context-unit
                      :orgpad/unit-visibility true})
              [:db/add (ot/uid unit-tree) :orgpad/props-refs -1]
              (when style
                [:db/add (ot/uid unit-tree) :orgpad/props-refs (:db/id style)])])
            (as-> s
                  (mutate (assoc env :state s :force-update! nop)
                          :orgpad/root-view-conf [unit-tree
                                                  {:attr :orgpad/view-type
                                                   :value :orgpad/atomic-view}]))
            :state
            (store/with-history-mode :add))]
    {:state new-state}))

(defmethod mutate :orgpad.units/map-move-to-unit
  [{:keys [state global-cache]} _ {:keys [uid vprop parent-view]}]
  (let [parent-id (-> parent-view :orgpad/refs first :db/id)
        position (:orgpad/unit-position vprop)
        transform (:orgpad/transform parent-view)
        bb (dom/dom-bb->bb (aget global-cache parent-id "bbox"))
        [w h] (-- (bb 1) (bb 0))
        new-translate (-- [(/ w 2) (/ h 2)] (*c position (-> transform :scale)))
        new-transformation (merge transform {:translate new-translate})]
    {:state (if (:db/id parent-view)
              (store/transact state [[:db/add (:db/id parent-view) :orgpad/transform new-transformation]])
              (store/transact state [(merge parent-view
                                            {:db/id -1
                                             :orgpad/refs parent-id
                                             :orgpad/transform new-transformation
                                             :orgpad/type :orgpad/unit-view})
                                     [:db/add parent-id :orgpad/props-refs -1]]))}))

(defmethod read :orgpad/selection-vertex-props
  [{:keys [state query] :as env} _ {:keys [selection id view]}]
  (group-by first
            (ot/get-child-props-from-db state id
                                        [[:orgpad/type :orgpad/unit-view-child]
                                         [:orgpad/view-name (:orgpad/view-name view)]
                                         [:orgpad/view-type :orgpad.map-view/vertex-props]
                                         [:orgpad/context-unit id]]
                                        selection)))

(defmethod read :orgpad/selection-text-props
  [{:keys [state query] :as env} _ {:keys [selection id view]}]
  (group-by first
            (ot/get-descendant-props-from-db state id
                                             [[:orgpad/type :orgpad/unit-view]
                                              [:orgpad/view-name (:orgpad/view-name view)]
                                              [:orgpad/view-type :orgpad/atomic-view]]
                                             selection)))

(defmethod mutate :orgpad.units/map-view-canvas-zoom
  [{:keys [state global-cache]} _ {:keys [view parent-id pos zoom]}]
  (let [z (* (-> view :orgpad/transform :scale) zoom)
        z' (if (< z 0.3) 0.3 z)
        p (screen->canvas (:orgpad/transform view) pos)
        translate (-- pos (*c p z'))
        transf {:translate translate :scale z'}]
    {:state (if (:db/id view)
              (store/transact state [[:db/add (:db/id view) :orgpad/transform transf]])
              (store/transact state [(merge view
                                            {:db/id -1
                                             :orgpad/refs parent-id
                                             :orgpad/transform transf
                                             :orgpad/type :orgpad/unit-view})
                                     [:db/add parent-id :orgpad/props-refs -1]]))}))

(defmethod mutate :orgpad.units/paste-to-map
  [{:keys [state global-cache]} _ {:keys [pid data position view-name transform]}]
  (let [pos (screen->canvas transform position)
        data' (assoc data :entities (ot/update-children-position data pos 0.5))
        {:keys [db temp->ids]} (ot/past-descendants-to-db state pid data')]
    (doseq [u (ot/get-all-paste-children-bbox data')]
      (let [rid (if (= (:ctx-unit u) (:old-pid data'))
                  pid
                  (-> u :ctx-unit - temp->ids))]
        (geocache/create! global-cache rid (:view-name u))
        (geocache/update-box! global-cache
                              rid
                              (:view-name u)
                              (-> u :uid temp->ids) (ot/left-top (:pos u) (:size u))
                              (-> u :size geom/ensure-size))))
    {:state db}))

(defmethod mutate :orgpad.units/map-view-link-swap-dir
  [{:keys [state]} _ {:keys [db/id orgpad/refs-order] :as unit}]
  (let [new-state (store/transact state (ot/make-swap-refs-order-qry id refs-order))]
    {:state new-state}))
