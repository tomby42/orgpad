(ns orgpad.tools.orgpad-db
  (:require [datascript.core :as ds]
            [orgpad.core.store :as store]
            [orgpad.tools.dscript :as dscript]
            [orgpad.tools.datom :as datom]
            [orgpad.tools.colls :as colls]
            [orgpad.tools.geom :refer [++ -- *c] :as geom]
            [orgpad.tools.bezier :as bez]
            [orgpad.components.registry :as reg]
            [orgpad.tools.orgpad :as ot]
            [goog.string :as gstring]
            [goog.string.format]))

(def ^:private base-qry
  '[:find ?pred ?p
    :in $ ?u ?type ?view-name ?view-type
    :where
    [?p :orgpad/type ?type]
    [?p :orgpad/view-name ?view-name]
    [?p :orgpad/view-type ?view-type]
    [?p :orgpad/refs ?pred]])

(defn- get-vprop-by-type
  [db uid type view-name view-type]
  (js/console.log (into base-qry '[[?u :orgpad/props-refs ?p]]))
  (-> db
      (store/query (into base-qry '[[?u :orgpad/props-refs ?p]])
                   [uid type view-name view-type])
      first))

(defn- get-vprop
  [db uid view-name view-type]
  (get-vprop-by-type db uid :orgpad/unit-view-child view-name view-type))

(defn- get-vprop-prop
  [db uid view-name view-type]
  (get-vprop-by-type db uid :orgpad/unit-view-child-propagated view-name view-type))

(defn- get-pred-vprop
  [db uid view-name view-type]
  (-> db
      (store/query (into base-qry '[[?v :orgpad/refs ?u]
                                    [?v :orgpad/props-refs ?p]])
                   [uid :orgpad/unit-view-child view-name view-type])
      first))

(defn- get-pred-active-unit
  [db uid view-name]
  (-> db
      (store/query '[:find ?u ?active-unit
                     :in $ ?v ?view-name
                     :where
                     [?u :orgpad/refs ?v]
                     [?u :orgpad/props-refs ?w]
                     [?w :orgpad/type :orgpad/unit-view]
                     [?w :orgpad/view-type :orgpad/map-tuple-view]
                     [?w :orgpad/view-name ?view-name]
                     [?w :orgpad/active-unit ?active-unit]]
                   [uid view-name])
      first))

(defn- size-qry
  [qry eid w h]
  (-> qry
      (conj [:db/add eid :orgpad/unit-width w])
      (conj [:db/add eid :orgpad/unit-height h])))

(defn update-vsize-qry
  [db uid view-name [w h]]
  (let [vprop-prop (get-vprop-prop db uid view-name :orgpad.map-view/vertex-props)
        vprop (get-vprop db uid view-name :orgpad.map-view/vertex-props)
        pred-vprop (get-pred-vprop db uid view-name :orgpad.map-view/vertex-props)
        pred-active (get-pred-active-unit db uid view-name)
        pred (or (first pred-active) (first pred-vprop))
        refs (when pred
               (-> db (store/query [:entity pred])  (ot/sort-refs :db/id)))
        current (when (and pred refs)
                  (-> refs (nth (or (nth pred-active 1) 0)) :db/id))
        actual (or (first vprop-prop) (first vprop))]
    (js/console.log "update-vsize-qry" uid view-name vprop-prop vprop pred-vprop pred-active pred refs current)
    (cond-> []
      vprop-prop (size-qry (nth vprop-prop 1) w h)
      vprop (size-qry (nth vprop 1) w h)
      (and pred-vprop
           (= current actual)) (size-qry (nth pred-vprop 1) w h))))
