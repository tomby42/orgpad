(ns orgpad.parsers.styles.parser
  (:require [clojure.set :as set]
            [com.rpl.specter :refer [keypath]]
            [datascript.transit :as dt]
            [orgpad.core.store :as store]
            [orgpad.core.orgpad :as orgpad]
            [orgpad.parsers.default-unit :as dp :refer [read mutate updated?]]
            [orgpad.tools.colls :as colls]
            [orgpad.tools.orgpad :as ot]
            [orgpad.components.registry :as registry]))

(defmethod read :orgpad/styles
  [{:keys [state]} _ {:keys [view-type]}]
  (store/query state '[:find [(pull ?e [*]) ...]
                       :in $ ?view-type
                       :where
                       [?e :orgpad/view-type ?view-type]]
               [view-type]))

(defmethod read :orgpad/style
  [{:keys [state]} _ {:keys [view-type style-name]}]
  (ot/get-style-from-db state view-type style-name))

(defmethod mutate :orgpad.style/update
  [{:keys [state]} _ {:keys [style prop-name prop-val]}]
  ;; (js/console.log "orgpad.style/update" style prop-name prop-val)
  {:state (store/transact state [[:db/add (:db/id style) prop-name prop-val]])})

(defmethod mutate :orgpad.style/new
  [{:keys [state]} _ {:keys [type name based-on]}]
  (let [style (-> state
                  (ot/get-style-from-db type based-on)
                  (assoc :orgpad/style-name name :db/id -1))
        new-state (store/transact state [style])]
    ;; (js/console.log "orgpad.style/new" type name style)
    {:state new-state}))

(defn- uids-with-props-refs
  [state id]
  (let [find-all '[:find [?u ...]
                   :in $ ?id
                   :where [?u :orgpad/props-refs ?id]]]
    (store/query state find-all [id])))

(defn- add-props-refs-id-query
  [uids id]
  (map #(vector :db/add % :orgpad/props-refs id) uids))

(defn- change-props-refs-id-query
  [uids old-id new-id]
  (let [remove-query (map #(vector :db/retract % :orgpad/props-refs old-id) uids)
        add-query (map #(vector :db/add % :orgpad/props-refs new-id) uids)]
    (into remove-query add-query)))

(def ^:private find-all '[:find [?p ...]
                          :in $ ?name ?prop-type
                          :where
                          [?p :orgpad/view-type ?prop-type]
                          [?p :orgpad/view-style ?name]])

(defn- uids-with-style
  "Get all unit ids having given style."
  [state name type]
  (let [prop-type (ot/prop-type-style->prop-type type)]
    (store/query state find-all [name prop-type])))

(defn- change-style-name-query
  [uids old-name new-name]
  (let [remove-query (map #(vector :db/retract % :orgpad/view-style old-name) uids)
        add-query (map #(vector :db/add % :orgpad/view-style new-name) uids)]
    (into remove-query add-query)))

(defmethod mutate :orgpad.style/remove
  [{:keys [state]} _ {:keys [id name type replace-name]}]
  (let [replace-name' (if replace-name replace-name "default")
        replace-id (:db/id (ot/get-style-from-db state type replace-name'))
        name-uids (uids-with-style state name type)
        props-refs-uids (uids-with-props-refs state id)]
    ;(js/console.log "orgpad.style/remove" id name type replace-name' replace-id)
    {:state (store/transact state
                            (colls/minto [[:db.fn/retractEntity id]]
                                         (change-style-name-query name-uids name replace-name')
                                         (add-props-refs-id-query props-refs-uids replace-id)))}))

(defmethod mutate :orgpad.style/rename
  [{:keys [state]} _ {:keys [id old-name new-name type]}]
  (let [uids (uids-with-style state old-name type)
        rename-query [[:db/retract id :orgpad/style-name old-name]
                      [:db/add id :orgpad/style-name new-name]]]
    ;(js/console.log "orgpad.style/rename" id old-name new-name type)
    {:state (store/transact state
                            (into rename-query (change-style-name-query uids old-name new-name)))}))

(defmethod mutate :orgpad.style/rebase
  [{:keys [state]} _ {:keys [id name type based-on]}]
  (let [style (-> state
                  (ot/get-style-from-db type based-on)
                  (assoc :orgpad/style-name name :db/id id))]
    ;(js/console.log "orgpad.style/rebase" id name type based-on style)
    {:state (store/transact state [style])}))
