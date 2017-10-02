(ns ^{:doc "Orgpad tools"}
  orgpad.tools.orgpad
  (:require [orgpad.core.store :as store]
            [orgpad.tools.dscript :as dscript]
            [orgpad.tools.geom :as geom]))

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
       (= (v :orgpad/view-name) view-name)))

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

(defn get-props-view-child
  [props view-name pid prop-name]
  (get-props props view-name pid prop-name :orgpad/unit-view-child))

(defn child-vertex-props
  [prop-fn unit-tree & [selection]]
  (let [name (view-name unit-tree)
        id (uid unit-tree)]
    (filter #(-> % nil? not)
            (map (fn [u]
                   (let [prop (-> u
                                  :props
                                  (get-props-view-child name id :orgpad.map-view/vertex-props))]
                     (prop-fn prop)))
                 (if selection
                   (filter #(contains? selection (uid %)) (refs unit-tree))
                   (refs unit-tree))))))

(defn child-bbs
  [unit-tree & [selection]]
  (child-vertex-props (fn [prop]
                        (when prop
                          (let [bw (* 2 (:orgpad/unit-border-width prop))]
                            [(:orgpad/unit-position prop)
                             (geom/++ (:orgpad/unit-position prop)
                                      [(:orgpad/unit-width prop) (:orgpad/unit-height prop)]
                                      [bw bw])])))
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

(defn get-descendant-props-qry
  [props-constraints]
  (into '[:find ?u1 ?u2 ?prop
          :in $ % ?p ?is-selected
          :where
          [?p :orgpad/refs ?u1]
          [(?is-selected ?u1)]
          (descendant ?u1 ?u2)
          [?u2 :orgpad/props-refs ?prop]]
        (map (fn [[prop-name prop-value]]
               `[~'?prop ~prop-name ~prop-value])
             props-constraints)))

(defn get-descendant-props-from-db
  [db pid props-constraints & [selection]]
  (let [children-props (store/query db
                                    (get-descendant-props-qry props-constraints)
                                    [rules pid (if (nil? selection)
                                                 (constantly true)
                                                 selection)])]
    (map (fn [[uid1 uid2 prop-id]]
           [uid1 (-> db (store/query [:entity prop-id]) dscript/entity->map) uid2])
         children-props)))
