(ns orgpad.parsers.utils
  (:require [orgpad.core.store :as store]
            [orgpad.tools.dscript :as ds]
            [orgpad.tools.orgpad :as ot]))

(defn get-view-props
  [unit {:keys [orgpad/view-type orgpad/view-name orgpad/type]}]
  (ds/find-props-all unit (partial ot/props-pred-no-ctx view-name
                                   view-type type)))

(defn get-path-info
  [unit view-path]
  (ds/find-props unit (fn [u]
                        (and (= (u :orgpad/view-path) view-path)
                             (= (u :orgpad/type) :orgpad/unit-path-info)))))

(defn get-o-create-path-info
  [unit view-name view-type view-path]
  (assoc (or (get-path-info unit view-path)
             {:orgpad/view-name view-name
              :orgpad/view-type view-type
              :orgpad/view-path view-path})
         :orgpad/type :orgpad/unit-view))

(defn- update-children-by-visible-children-picker
  [eunit unit view-unit old-node view-info parser' global-cache refs]
  (let [sort-refs (if (:orgpad/refs-order eunit)
                    (map second (:orgpad/refs-order eunit))
                    [])] ;; TODO: hack for links that are units too - need to get all siblings of current unit
    (mapv (fn [[u o]] (parser' u o))
          ((:orgpad/visible-children-picker view-info)
           unit view-unit
           (if (and old-node (= (aget old-node "key") :orgpad/unit-view))
             old-node nil)
           global-cache sort-refs))))

(defn- update-all-children
  [eunit parser' old-children-nodes use-children-nodes? refs]
  (let [refs' (map :db/id refs)]
    ;; (println (eunit :orgpad/refs-order))
    (if (eunit :orgpad/refs-order)
      (let [children (if use-children-nodes?
                       (into {} (map (juxt identity parser')
                                     refs' old-children-nodes))
                       (into {} (map (juxt identity parser') refs')))]
        (mapv (comp children second) (eunit :orgpad/refs-order)))
      (if use-children-nodes?
        (into [] (map parser' refs' old-children-nodes))
        (mapv parser' refs')))))

(defn- update-children-by-non-expanding-sort
  [eunit refs]
  (let [children (into {} (map (juxt :db/id identity) refs))]
    (mapv (comp children second) (eunit :orgpad/refs-order))))

(defn- expand-units
  [pred parser' from start-idx max-idx max-cnt cache refs]
  (let [cache' (if cache
                 (if (not @cache)
                   (make-array (inc max-idx))
                   @cache)
                 nil)]
    (when (and cache (not @cache))
      (vreset! cache cache'))
    (loop [cnt 0
           idx start-idx
           expanded (transient [])]
      (if (or (= cnt max-cnt) (> idx max-idx))
        [(persistent! expanded)
         (if (> idx max-idx) nil (subvec refs idx))
         cnt]
        (let [u (parser' (-> refs (get idx) :db/id))
              use? (pred u)]
          (when (and cache' use?)
            (aset cache' (+ from cnt) idx))
          (recur (if use? (inc cnt) cnt)
                 (inc idx)
                 (conj! expanded (if use? u (assoc u :filtered? true)))))))))

(defn- update-children-by-expande-range
  [eunit parser' [pred prefix from start num cache] refs]
  (let [refs' (if (:orgpad/refs-order eunit)
                (update-children-by-non-expanding-sort eunit refs)
                (vec refs))
        max-idx (-> refs' count dec)
        start' (min start max-idx)

        [expanded rs iterated]
        (if (= max-idx -1)
          [nil nil 0]
          (expand-units pred parser' from start' max-idx num cache
                        (subvec refs' start')))

        end (min (+ start' iterated) (inc max-idx))]
    (if (= max-idx -1)
      []
      (with-meta
        (into []
              (concat (if prefix prefix (subvec refs' 0 start'))
                      expanded
                      rs))
        {:next end}))))

(defn build-unit
  [unit view-unit old-node view-info parser' global-cache
   {:keys [recur-level max-recur-level all-children? expand-range-fn]}]
  (let [eunit (ds/entity->map unit)]
    ;; (js/console.log "build-unit" (:db/id eunit) recur-level max-recur-level)
    (if (and (:orgpad/needs-children-info view-info)
             (< recur-level max-recur-level))
      (if (and (:orgpad/visible-children-picker view-info)
               (not all-children?))
        (update eunit :orgpad/refs
                (partial update-children-by-visible-children-picker
                         eunit unit view-unit old-node view-info parser'
                         global-cache))
        (if expand-range-fn
          (update eunit :orgpad/refs
                  (partial update-children-by-expande-range eunit parser'
                           (expand-range-fn eunit view-unit recur-level)))
          (let [old-children-nodes (and old-node (aget old-node "children"))
                use-children-nodes? (and old-node
                                         (= (aget old-node "key")
                                            :orgpad/unit-view)
                                         (= (alength old-children-nodes)
                                            (count (unit :orgpad/refs))))]
            (update eunit :orgpad/refs
                    (partial update-all-children
                             eunit parser' old-children-nodes
                             use-children-nodes?)))))
      (if (:orgpad/refs-order eunit)
        (update eunit :orgpad/refs
                (partial update-children-by-non-expanding-sort eunit))
        eunit))))
