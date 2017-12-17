(ns ^{:doc "Orgpad tools"}
  orgpad.tools.orgpad
  (:require [orgpad.core.store :as store]
            [orgpad.tools.dscript :as dscript]
            [orgpad.tools.colls :as colls]
            [orgpad.tools.geom :refer [++ -- *c] :as geom]
            [goog.string :as gstring]
            [goog.string.format]))

(defn uid
  [unit]
  (-> unit :unit :db/id))

(defn uid-safe
  [unit]
  (or (uid unit) (:db/id unit)))

(defn sort-refs
  [unit]
  (into [] (sort #(compare (uid %1) (uid %2)) (unit :orgpad/refs))))

(defn pid
  [parent-view]
  (-> parent-view :orgpad/refs first :db/id))

(defn view-name
  [unit]
  (-> unit :view :orgpad/view-name))

(defn view-type
  [unit]
  (-> unit :view :orgpad/view-type))

(defn refs
  [unit]
  (-> unit :unit :orgpad/refs))

(defn refs-uid
  [unit]
  (->> unit :unit :orgpad/refs (map uid)))

(defn refs-count
  [unit]
  (-> unit refs count))

(defn get-sorted-ref
  [unit idx]
  (get (sort-refs unit) idx))

(defn active-child-tree
  [unit view]
  (let [active-child (-> view :orgpad/active-unit)]
    (get-sorted-ref unit active-child)))

(defn get-sheet-number
  [{ :keys [unit view]}]
  [(-> view :orgpad/active-unit inc) (-> unit :orgpad/refs count)])

(defn no-sheets?
  [unit-tree]
  (= ((get-sheet-number unit-tree) 1) 0))

(defn first-sheet?
  [unit-tree]
  (= ((get-sheet-number unit-tree) 0) 1))

(defn last-sheet?
  [unit-tree]
  (let [[current-sheet sheet-count] (get-sheet-number unit-tree)]
    (>= current-sheet sheet-count)))

(defn sheets-to-str
  [unit-tree]
  (if (no-sheets? unit-tree)
    "none"
    (apply gstring/format "%d/%d" (get-sheet-number unit-tree)))) 

(defn update-unit-view-query
  [unit-id view key val]
  (if (view :db/id)
    [[:db/add (view :db/id) key val]]
    [(merge view
            { :db/id -1
              :orgpad/refs unit-id
              key val
              :orgpad/type :orgpad/unit-view })
     [:db/add unit-id :orgpad/props-refs -1] ]))

(def ^:private rules
  '[[(eq [?e3 ?e4])
     [(= ?e3 ?e4)]]
    [(atom ?e1 ?e2 ?a)
     [?e2 :orgpad/props-refs ?prop]
     [?prop :orgpad/atom ?a]]
    [(atom ?e1 ?e2 ?a)
     [?e1 :orgpad/props-refs ?prop]
     [?prop :orgpad/atom ?a]]
    [(descendant ?e1 ?e2)
     (eq ?e1 ?e2)]
    [(descendant ?e1 ?e2)
     [?e1 :orgpad/refs ?e2]]
    [(descendant ?e1 ?e2)
     [?e1 :orgpad/refs ?t]
     (descendant ?t ?e2)]])

(defn- contains-pattern?
  [pattern text]
  (not (empty? (re-seq (re-pattern pattern) text))))

(defn search-child-by-descendant-txt-pattern
  [store uid pattern]
  (store/query store
               '[:find  ?u1 ?u2
                 :in    $ % ?p ?contains-text
                 :where
                 [?p :orgpad/refs ?u1]
                 (descendant ?u1 ?u2)
                 (atom ?u1 ?u2 ?a)
                 [(?contains-text ?a)]]
               [rules uid (partial contains-pattern? pattern)]))

(defn props-pred-no-ctx
  [view-name view-type type v]
  (and v
       (= (v :orgpad/view-type) view-type)
       (= (v :orgpad/type) type)
       (or (= (v :orgpad/view-name) view-name)
           (= (v :orgpad/view-name) "*"))))

(defn props-pred
  [ctx-unit view-name view-type type v]
  (and (props-pred-no-ctx view-name view-type type v)
       (= (v :orgpad/context-unit) ctx-unit)))

(defn props-pred-view-child
  [ctx-unit view-name view-type v]
  (props-pred ctx-unit view-name view-type :orgpad/unit-view-child v))

(defn get-props-no-ctx
  [props view-name prop-name prop-type]
  (->> props
       (drop-while #(not (props-pred-no-ctx view-name prop-name prop-type %)))
       first))

(defn get-props
  [props view-name pid prop-name prop-type]
  (->> props
       (drop-while #(not (props-pred pid view-name prop-name prop-type %)))
       first))

(defn get-props-all
  [props view-name prop-name prop-type]
  (filter (partial props-pred-no-ctx view-name prop-name prop-type) props))

(defn get-props-view-child
  [props view-name pid prop-name]
  (get-props props view-name pid prop-name :orgpad/unit-view-child))

(defn get-props-view-child-all
  [props view-name prop-name]
  (get-props-all props view-name prop-name :orgpad/unit-view-child))

(defn get-style
  [props style-name style-type]
  (let [styles (get-props-view-child-all props "*" style-type)]
    (->> styles (drop-while #(not= (:orgpad/style-name %) style-name)) first)))

(defn get-props-view-child-styled
  [props view-name pid prop-name style-type]
  (let [prop (get-props-view-child props view-name pid prop-name)
        style (get-style props (:orgpad/view-style prop) style-type)]
    (merge style prop)))

(defn child-vertex-props
  [prop-fn unit-tree & [selection]]
  (let [name (view-name unit-tree)
        id (uid unit-tree)]
    (filter #(-> % nil? not)
            (map (fn [u]
                   (let [prop (-> u
                                  :props
                                  (get-props-view-child-styled name id
                                                               :orgpad.map-view/vertex-props
                                                               :orgpad.map-view/vertex-props-style))]
                     (prop-fn prop (uid u))))
                 (if selection
                   (filter #(contains? selection (uid %)) (refs unit-tree))
                   (refs unit-tree))))))

(defn child-bbs
  [unit-tree & [selection]]
  (child-vertex-props (fn [prop id]
                        (when prop
                          (let [bw (* 2 (:orgpad/unit-border-width prop))]
                            {:bb [(:orgpad/unit-position prop)
                                  (++ (:orgpad/unit-position prop)
                                      [(:orgpad/unit-width prop) (:orgpad/unit-height prop)]
                                      [bw bw])]
                             :id id})))
                      unit-tree selection))

(defn get-ref-by-uid
  [unit-tree id]
  (->> unit-tree
       refs
       (drop-while #(not= id (uid %)))
       first))

(defn get-pos
  [unit-tree view-name pid]
  (-> unit-tree
      :props
      (get-props-view-child view-name pid :orgpad.map-view/vertex-props)
      :orgpad/unit-position))

(defn get-child-props-qry
  [props-constraints]
  (into '[:find ?u1 ?u2
          :in $ ?p ?is-selected
          :where
          [?p :orgpad/refs ?u1]
          [(?is-selected ?u1)]
          [?u1 :orgpad/props-refs ?u2]]
        (map (fn [[prop-name prop-value]]
               `[~'?u2 ~prop-name ~prop-value])
             props-constraints)))

(defn get-child-props-from-db
  [db pid props-constraints & [selection]]
  (let [children-props (store/query db
                                    (get-child-props-qry props-constraints)
                                    [pid (if (nil? selection)
                                           (constantly true)
                                           selection)])]
    (map (fn [[uid prop-id]]
           [uid (-> db (store/query [:entity prop-id]) dscript/entity->map)])
         children-props)))

(def ^:protect prop-rules
  '[[(prop ?e1 ?e2 ?prop)
     [(nil? ?e2)]
     [?e1 :orgpad/props-refs ?prop]]
    [(prop ?e1 ?e2 ?prop)
     [?e2 :orgpad/props-refs ?prop]]])

(defn get-descendant-props-qry
  [props-constraints]
  (into '[:find ?u1 ?u2 ?prop
          :in $ % ?p ?is-selected
          :where
          [?p :orgpad/refs ?u1]
          [(?is-selected ?u1)]
          (descendant ?u1 ?u2)
          (prop ?u1 ?u2 ?prop)]
        (map (fn [[prop-name prop-value]]
               `[~'?prop ~prop-name ~prop-value])
             props-constraints)))

(defn get-descendant-props-from-db
  [db pid props-constraints & [selection]]
  (let [children-props (store/query db
                                    (get-descendant-props-qry props-constraints)
                                    [(into rules prop-rules) pid
                                     (if (nil? selection)
                                       (constantly true)
                                       selection)])]
    (map (fn [[uid1 uid2 prop-id]]
           [uid1 (-> db (store/query [:entity prop-id]) dscript/entity->map) uid2])
         children-props)))

(defn- negate-db-id
  [u]
  (mapv (fn [v] (-> v :db/id -)) u))

(defn- negate-db-id-but-independent
  [inds u]
  (mapv (fn [v] (let [id (:db/id v)]
                  (if (contains? inds id)
                    id
                    (- id))))
        u))

(defn- get-roots
  [db pid selection]
  (if (nil? selection)
    (into #{} (map (comp - :db/id) (-> db (store/query [:entity pid]) :orgpad/refs)))
    (into #{} (map -) selection)))

(defn- update-refs-order
  [update-uid refs-orders]
  (apply sorted-set
         (map (fn [[n uid]]
                [n (update-uid uid)]) refs-orders)))

(defn copy-descendants-from-db
  [db pid props-constraints & [selection]]
  (let [raw-props (get-descendant-props-from-db db pid props-constraints selection)
        groups (group-by #(-> % second :orgpad/independent) raw-props)
        props (groups nil)
        iprops (into {} (map #(vector (-> % second :db/id) true)) (groups true))
        units-ids (->> props (map #(or (get % 2) (get % 0))) set)
        update-refs-order' (partial update-refs-order -)
        negate-db-id-but-independent' (partial negate-db-id-but-independent iprops)]
    {:entities
     (colls/minto
      (sorted-set-by #(compare (-> %1 :db/id -) (-> %2 :db/id -)))
      (map #(-> db
                (store/query [:entity %])
                dscript/entity->map
                (update :db/id -)
                (as-> e
                    (cond-> e
                      (:orgpad/refs e) (update :orgpad/refs negate-db-id)
                      (:orgpad/props-refs e) (update :orgpad/props-refs negate-db-id-but-independent')
                      (:orgpad/refs-order e) (update :orgpad/refs-order update-refs-order')))) units-ids)
      (map #(-> %
                second
                (update :db/id -)
                (update :orgpad/refs negate-db-id)) props))
     :roots (get-roots db pid selection)}))

(defn past-descendants-to-db
  [db pid {:keys [entities roots]}]
  (let [db1 (store/transact db (colls/minto [] entities (map #(vector :db/add pid :orgpad/refs %) roots)))
        temp->ids (store/tempids db1)
        ref-orders-qupdate (into [] (comp (filter :orgpad/refs-order)
                                          (map (fn [unit]
                                                 [:db/add (temp->ids (:db/id unit))
                                                  :orgpad/refs-order
                                                  (update-refs-order temp->ids (:orgpad/refs-order unit))
                                                  ])))
                                 entities)
        db2 (if (empty? ref-orders-qupdate) db1 (store/transact db1 ref-orders-qupdate))]
    {:db db2 :temp->ids temp->ids}))

(defn- is-vertex-prop
  [roots p]
  (and (contains? roots (-> p :orgpad/refs first))
       (contains? p :orgpad/unit-position)))

(defn update-children-position
  [{:keys [entities roots]} new-pos weight]
  (let [bb (apply geom/points-bbox
                  (sequence
                   (comp (filter #(is-vertex-prop roots %))
                         (map :orgpad/unit-position))
                   entities))
        delta (-- new-pos (*c (++ (bb 0) (bb 1)) weight))]
    (map (fn [e]
           (if (is-vertex-prop roots e)
             (update e :orgpad/unit-position #(++ % delta))
             e)) entities)))

(defn get-paste-children-bbox
  [{:keys [entities roots]}]
  (sequence
   (comp (filter #(is-vertex-prop roots %))
         (map #(hash-map :uid (-> % :orgpad/refs first)
                         :pos (:orgpad/unit-position %)
                         :size [(:orgpad/unit-width %) (:orgpad/unit-height %)])))
   entities))

(defn get-prop-from-db-styles
  [state props prop style-type]
  (let [id (prop :db/id)
        prop' (if id (store/query state [:entity id]) prop)
        style (get-style props (:orgpad/view-style prop') style-type)
        style' (dscript/entity->map (store/query state [:entity (:db/id style)]))]
    (merge style' prop')))

(defn get-prop-style
  [state prop style-type]
  (store/query state '[:find (pull ?s [*]) .
                       :in $ ?style-name ?style-type
                       :where
                       [?s :orgpad/style-name ?style-name]
                       [?s :orgpad/view-type ?style-type]]
               [(:orgpad/view-style prop) style-type]))
