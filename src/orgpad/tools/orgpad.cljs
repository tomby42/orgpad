(ns ^{:doc "Orgpad tools"}
  orgpad.tools.orgpad
  (:require [orgpad.core.store :as store]
            [orgpad.tools.dscript :as dscript]
            [orgpad.tools.colls :as colls]
            [orgpad.tools.geom :refer [++ -- *c] :as geom]
            [orgpad.tools.bezier :as bez]
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
  (let [active-child (:orgpad/active-unit view)]
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
  (apply sorted-set-by colls/first-<
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
     :old-pid pid
     :roots (get-roots db pid selection)}))

(defn make-roots-query
  [pid roots]
  (map #(vector :db/add pid :orgpad/refs %) roots))

(defn make-ref-orders-updates-qry
  [entities temp->ids & [update-uid]]
  (let [update-uid (if update-uid update-uid identity)]
    (into [] (comp (filter :orgpad/refs-order)
                   (map (fn [unit]
                          [:db/add (temp->ids (:db/id unit))
                           :orgpad/refs-order
                           (update-refs-order (comp temp->ids update-uid) (:orgpad/refs-order unit))
                           ])))
        entities)))

(defn update-path-info-qry
  [entities temp->ids old-rpid new-rpid]
  (into [] (comp (filter :orgpad/view-path)
                 (map (fn [path-info]
                        [:db/add (temp->ids (:db/id path-info))
                         :orgpad/view-path
                         (mapv #(if (number? %)
                                  (if (= old-rpid %)
                                    new-rpid
                                    (temp->ids (- %)))
                                  %) (:orgpad/view-path path-info))])))
        entities))

(defn make-context-unit-update-qry
  [entities temp->ids old-rpid new-rpid]
  (into []
        (comp (filter :orgpad/context-unit)
              (map (fn [unit]
                     (let [ctx-unit (:orgpad/context-unit unit)]
                     [:db/add (temp->ids (:db/id unit))
                      :orgpad/context-unit
                      (if (= ctx-unit old-rpid)
                        new-rpid
                        (temp->ids (- ctx-unit)))]))))
        entities))

(defn past-descendants-to-db
  [db new-pid {:keys [entities roots old-pid]}]
  (let [db1 (-> db
                (store/with-history-mode {:new-record true
                                          :mode :acc})
                (store/transact (colls/minto [] entities (make-roots-query new-pid roots))))
        temp->ids (store/tempids db1)
        path-info-update (update-path-info-qry entities temp->ids old-pid new-pid)
        context-unit-update (make-context-unit-update-qry entities temp->ids old-pid new-pid)
        ref-orders-qupdate (make-ref-orders-updates-qry entities temp->ids)
        update-qry (colls/minto path-info-update ref-orders-qupdate context-unit-update)
        db2 (if (empty? update-qry) db1 (store/transact db1 update-qry))]
    {:db (store/with-history-mode db2 :add) :temp->ids temp->ids}))

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

(defn get-all-paste-children-bbox
  [{:keys [entities]}]
  (sequence
   (comp (filter :orgpad/unit-position)
         (map #(hash-map :uid (-> % :orgpad/refs first)
                         :ctx-unit (:orgpad/context-unit %)
                         :view-name (:orgpad/view-name %)
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

(defn get-style-from-db
  [state style-type style-name]
  (store/query state '[:find (pull ?s [*]) .
                       :in $ ?style-name ?style-type
                       :where
                       [?s :orgpad/style-name ?style-name]
                       [?s :orgpad/view-type ?style-type]]
               [style-name style-type]))

(defn get-all-unit-styles-names
  [state uid]
  (-> state
      (store/query '[:find [?style ...]
                     :in $ ?uid
                     :where
                     [?ppp :orgpad/type :orgpad/unit-view-child-propagated]
                     [?child :orgpad/props-refs ?ppp]
                     [?uid :orgpad/refs ?child]
                     [?ppp :orgpad/view-style ?style]]
                   [uid])
      (->> (into #{}))))

(defn- every-non-nil?
  [col]
  (every? #(-> % nil? not) col))

(defn gen-new-name
  [db {:keys [orgpad/style-name orgpad/view-type]}]
  (loop [n 1]
    (if (get-style-from-db db view-type (str style-name n))
      (recur (inc n))
      (str style-name n))))

(def get-type-view-style (juxt :orgpad/view-type :orgpad/view-style))
(def get-type-style-name (juxt :orgpad/view-type :orgpad/style-name))

(def prop-type->prop-type-style
  {:orgpad.map-view/vertex-props :orgpad.map-view/vertex-props-style
   :orgpad.map-view/link-props :orgpad.map-view/link-props-style})

(defn get-pos-props
  [db root-id]
  (-> db
      (store/query '[:find [?p ...]
                     :in $ ?r
                     :where
                     [?r :orgpad/refs ?e]
                     [?e :orgpad/props-refs ?p]
                     [?p :orgpad/unit-position]]
                   [root-id])
      (->> (into #{}))))

(defn merge-orgpads
  [db1 db2 pid translations]
  (let [entities (-> db2 (store/query
                          '[:find [(pull ?e [*]) ...]
                            :in $
                            :where
                            [?e :orgpad/type]])
                     (->> (sort-by :db/id)))
        roots (get-roots db2 0 nil)
        roots-pos-props (get-pos-props db2 0)
        entities-prep-1 (into [] (comp (filter #(not (or (= 0 (:db/id %))
                                                         (= 1 (:db/id %)))))
                                       (map #(update % :db/id -))
                                       (map #(update % :orgpad/refs
                                                     (fn [refs]
                                                       (into [] (map (comp - :db/id)) refs))))
                                       (map #(update % :orgpad/props-refs
                                                     (fn [prefs]
                                                       (into [] (map (comp - :db/id)) prefs)))))
                              entities)
        styles (->> entities-prep-1
                    (group-by get-type-style-name)
                    (filter (comp every-non-nil? first))
                    (into {}))
        new-style-name (->> styles
                            (into {} (map (fn [[k v]]
                                            (if (nil? (apply get-style-from-db db1 k))
                                              [k (get k 1)]
                                              [k (gen-new-name db1 (first v))])))))
        entities-qry
        (into []
              (comp
               (map (fn [e] ;; update styles name in props
                      (if (-> e get-type-view-style every-non-nil?)
                        (update e :orgpad/view-style
                                #(new-style-name [(prop-type->prop-type-style (:orgpad/view-type e)) %]))
                        e)))
               (map (fn [e] ;; update styles name of styles
                      (if (-> e get-type-style-name every-non-nil?)
                        (update e :orgpad/style-name
                                #(new-style-name [(:orgpad/view-type e) %]))
                        e)))
               (map (fn [e]
                      (if (and (:orgpad/unit-position e)
                               (contains? roots-pos-props (-> e :db/id -)))
                        (update e :orgpad/unit-position
                                #(-- % (get translations (:orgpad/view-name e) [0 0])))
                        e))))
              entities-prep-1)
        db1-1 (-> db1
                  (store/with-history-mode {:new-record true
                                            :mode :acc})
                  (store/transact (into entities-qry (make-roots-query pid roots))))
        temp->ids (store/tempids db1-1)
        path-info-update (update-path-info-qry entities-qry temp->ids 0 pid)
        ref-orders-qupdate (make-ref-orders-updates-qry entities-qry temp->ids -)
        context-unit-update (make-context-unit-update-qry entities-qry temp->ids 0 pid)
        update-qry (colls/minto path-info-update ref-orders-qupdate context-unit-update)]
    (->
     (if (empty? update-qry)
       db1-1
       (store/transact db1-1 update-qry))
     (store/with-history-mode :add))))

;; TODO: optimize
(defn get-links-from-db
  [db pid view-name]
  (let [links (store/query db '[:find [[?pt1 ?pt2 ?midpt ?lid] ...]
                                :in $ ?pid ?view-name
                                :where
                                [?pid :orgpad/refs ?lid]
                                [?lid :orgpad/props-refs ?p]
                                [?p :orgpad/view-name ?view-name]
                                [?p :orgpad/link-mid-pt ?midpt]
                                [?lid :orgpad/refs ?v1]
                                [?lid :orgpad/refs ?v2]
                                [?pid :orgpad/refs ?v1]
                                [?pid :orgpad/refs ?v2]
                                [?v1 :orgpad/props-refs ?vp1]
                                [?vp1 :orgpad/view-name ?view-name]
                                [?vp1 :orgpad/unit-position ?pt1]
                                [?v2 :orgpad/props-refs ?vp2]
                                [?vp2 :orgpad/view-name ?view-name]
                                [?vp2 :orgpad/unit-position ?pt2]])]
    links))

(defn- mapped?
  [{:keys [orgpad/refs db/id]} view-name prop-name]
  (let [pred (partial props-pred-view-child id view-name prop-name)]
    (filter (fn [u] (->> u :props (some pred))) refs)))

(defn mapped-children
  [unit-tree view-name]
  (mapped? unit-tree view-name :orgpad.map-view/vertex-props))

(defn mapped-links
  [unit-tree view-name pid m-units]
  (let [links (mapped? unit-tree view-name :orgpad.map-view/link-props)
        mus   (into {} (map (fn [u] [(uid u) u])) m-units)]
    (map (fn [l]
           (let [refs (-> l :unit :orgpad/refs)
                 id1 (-> refs (nth 0) uid-safe)
                 id2 (-> refs (nth 1) uid-safe)]
             [l {:start-pos (get-pos (mus id1) view-name pid)
                 :end-pos (get-pos (mus id2) view-name pid)
                 :cyclic? (= id1 id2) }]))
         links)))

(defn- link-dist-info
  [p {:keys [props unit]} {:keys [start-pos end-pos cyclic?]} pid view-name]
  (let [prop (get-props-view-child-styled props view-name pid
                                          :orgpad.map-view/link-props :orgpad.map-view/link-props-style)
        mid-pt (geom/link-middle-point start-pos end-pos (:orgpad/link-mid-pt prop))
        ctl-pt (geom/link-middle-ctl-point start-pos end-pos mid-pt)]
    (bez/nearest-point-on start-pos ctl-pt end-pos p)))

(defn get-nearest-link
  [{:keys [unit view props] :as unit-tree} p]
  (let [view-name (view :orgpad/view-name)
        pid (:db/id unit)
        vertices (mapped-children unit view-name)
        links (mapped-links unit view-name pid vertices)]
    (reduce (fn [[best-unit _ dist-info :as res] [unit info]]
              (let [d-info (link-dist-info p unit info pid view-name)]
                (if (< (.-d d-info) (.-d dist-info))
                  [unit info d-info]
                  res)))
            [nil nil #js {:d 100000000}] links)))

(defn make-swap-refs-order-qry
  [id refs-order]
  (let [f (first refs-order)
        s (second refs-order)
        new-refs-order (sorted-set-by colls/first-<
                                      [(get f 0) (get s 1)]
                                      [(get s 0) (get f 1)])]
    [[:db/retract id :orgpad/refs-order refs-order]
     [:db/add id :orgpad/refs-order new-refs-order]]))
