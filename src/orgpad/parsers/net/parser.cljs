(ns ^{:doc "Online parser"} orgpad.parsers.net.parser
  (:require [com.rpl.specter :refer [keypath]]
            [datascript.core :as d]
            [datascript.transit :as dt]
            [clojure.set :as set]
            [orgpad.core.store :as store]
            [orgpad.core.orgpad :as oc]
            [orgpad.parsers.default-unit :as dp :refer [read mutate updated?]]
            [orgpad.parsers.default-root :as dr :refer [resolve-parser-state!]]
            [orgpad.tools.dscript :as ds]
            [orgpad.tools.orgpad :as ot]
            [orgpad.tools.geocache :as geocache]
            [orgpad.tools.jcolls :as jscolls]
            [orgpad.tools.dom :as dom]
            [orgpad.components.registry :as registry]
            [orgpad.effects.net :as enet]
            [orgpad.config :as ocfg]))

(defmethod mutate :orgpad.net/connect-to-server
  [{:keys [transact! state]} _ [url ouid]]
  {:effect #(enet/connect! url ouid state transact!)})

(defmethod mutate :orgpad.net/set-db
  [{:keys [transact! state]} _ res]
  (let [{:keys [db atom]} (get-in res [:?data 1 :result])
        datom (dt/read-transit-str db)
        {:keys [datoms mapping last-index]}
        (ot/datoms-squuid->uid (seq datom))
        _ (when ocfg/*online-debug* (js/console.log "set-db" datom datoms atom last-index))
        new-state (store/new-datom-atom-store (-> atom
                                                  (assoc
                                                   :orgpad-uuid (get-in res [:?data 1 :params :orgpad.server/uuid])
                                                   :squuid->uid mapping
                                                   :uid-last-index last-index
                                                   :net-update-ignore? :all
                                                   :uid->squuid (set/map-invert mapping)))
                                              (-> (:schema datom)
                                                  d/empty-db
                                                  (d/with datoms)
                                                  :db-after))
        qry (ot/update-vprops-from-propagated-qry new-state)
        _ (when ocfg/*online-debug* (js/console.log "vprop update:" qry))
        new-state1 (if (empty? qry) new-state (store/transact new-state qry {}))]
    {:state (store/transact state [[:net-update-ignore?] :all])
     :effect #(transact! [[:orgpad/loaded new-state1]])}))

(defmethod mutate :orgpad.net/update-db
  [{:keys [state global-cache] :as env} _ res]
  (if (= (get-in res [:?data 1 :result]) :orgpad.server/error)
    (let [e (get-in res [:?data 1 :error])]
      (js/console.log "UPDATE ERROR: " (get-in res [:?data 1]))
      {:state state})
    (let [{:keys [db atom]} (get-in res [:?data 1 :result])
          datom (dt/read-transit-str db)
          uid->squuid (-> state (store/query [:uid->squuid]) first)
          last-index (-> state (store/query [:uid-last-index]) first)
          {:keys [datoms mapping last-index new-indices]}
          (ot/datoms-squuid->uid datom
                                 last-index
                                 (-> state (store/query [:squuid->uid]) first))
          datoms1 (ot/filter-vprop-when-not-active state datoms)
          _ (when ocfg/*online-debug* (js/console.log "filtering datoms" datoms datoms1))
          new-state (-> state
                        (store/transact datoms1)
                        (store/transact [[] (merge (-> state (store/query []) first)
                                                   atom
                                                   {:squuid->uid mapping
                                                    :uid->squuid (merge uid->squuid (set/map-invert new-indices))
                                                    :uid-last-index last-index
                                                    :net-update-ignore? :all})]))]
      (dr/resolve-parser-state! env new-state)
      (geocache/update-changed-units! global-cache state new-state (:datom (store/changed-entities new-state)))
      {:state new-state})))
