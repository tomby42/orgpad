(ns ^{:doc "Online parser"} orgpad.parsers.net.parser
  (:require [com.rpl.specter :refer [keypath]]
            [datascript.core :as d]
            [datascript.transit :as dt]
            [clojure.set :as set]
            [orgpad.core.store :as store]
            [orgpad.core.orgpad :as oc]
            [orgpad.parsers.default-unit :as dp :refer [read mutate updated?]]
            [orgpad.tools.dscript :as ds]
            [orgpad.tools.orgpad :as ot]
            [orgpad.tools.geocache :as geocache]
            [orgpad.tools.jcolls :as jscolls]
            [orgpad.tools.dom :as dom]
            [orgpad.components.registry :as registry]
            [orgpad.effects.net :as enet]))

(defmethod mutate :orgpad.net/connect-to-server
  [{:keys [transact! state]} _ [url ouid]]
  {:effect #(enet/connect! url ouid state transact!)})

(defmethod mutate :orgpad.net/set-db
  [{:keys [transact! state]} _ res]
  (let [{:keys [db atom]} (get-in res [:?data 1 :result])
        datom (dt/read-transit-str db)
        uid->squuid (:uid->squuid atom)
        {:keys [datoms mapping last-index]}
        (ot/datoms-squuid->uid (seq datom)
                               (when uid->squuid
                                 (->> uid->squuid keys (apply max) inc))
                               (when uid->squuid
                                 (set/map-invert uid->squuid)))
        _ (js/console.log datom datoms atom)
        new-state (store/new-datom-atom-store (-> atom
                                                  (assoc
                                                   :squuid->uid mapping
                                                   :uid-last-index last-index
                                                   :net-update-ignore? (if uid->squuid :all :global))
                                                  (cond->
                                                      (not uid->squuid) (assoc :uid->squuid (set/map-invert mapping))))
                                              (-> (:schema datom)
                                                  d/empty-db
                                                  (d/with datoms)
                                                  :db-after))]
    {:state state
     :effect #(transact! [[:orgpad/loaded new-state]])}))
