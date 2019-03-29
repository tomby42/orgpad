(ns orgpad.effects.net
  (:require
   [orgpad.net.com :as net]
   [cljs.core.async :as async :refer (<! >! put! chan close! put! alts! go-loop go)]
   [datascript.core :as d]
   [datascript.transit :as dt]
   [cemerick.url :as url]
   [orgpad.core.store :as store]
   [orgpad.tools.orgpad :as ot]
   [orgpad.config :as ocfg]
   [orgpad.tools.dscript :as ds]))

(defonce ^:private in-chan (chan 100))

(defn- discard-all-but
  [ch pred]
  (go-loop []
    (let [res (<! ch)]
      (if (-> res pred not)
        (recur)
        res))))

(defn- transform-db
  [db]
  (let [{:keys [datoms mapping]} (ot/datoms-uid->squuid (seq db))
        _ (when ocfg/*online-debug* (js/console.log "transform-db" datoms))
        new-db (ds/new-db-with-same-schema db datoms)]
    {:mapping mapping
     :db new-db}))

(defn- is-response?
  [res]
  (-> res
      (get-in [:?data 0])
      (= :orgpad.server/response)))

(defn connect!
  [url ouid state transact!]
  (let [{:keys [res-chan]} (net/start! url)]
    (go
      (let [_ (<! (discard-all-but res-chan #(-> % (get-in [:?data 1 :first-open?]) nil?)))
            _ (when ocfg/*online-debug* (js/console.log "Connected from effects"))
            _ (net/send-cmd {:action :orgpad.server/exist-orgpad? :params {:orgpad.server/uuid ouid}})
            res (<! (discard-all-but res-chan is-response?))
            _ (when ocfg/*online-debug* (js/console.log "exist-orgpad?" res))]
        (if (get-in res [:?data 1 :result])
          (let [_ (net/send-cmd {:action :orgpad.server/connect-to-orgpad
                                 :params {:orgpad.server/uuid ouid}})
                data (<! (discard-all-but res-chan is-response?))]
            (transact! [[:orgpad.net/set-db data]])
            (loop []
              (let [[v ch] (alts! [res-chan in-chan])]
                (when v
                  (when (= ch res-chan)
                    (case (get-in v [:?data 1 :action])
                      :orgpad.server/update-orgpad (transact! [[:orgpad.net/update-db v]])
                      nil))
                  (recur)))))
          (let [{:keys [db]} (transform-db (-> state .-datom .-db))
                _ (when ocfg/*online-debug* (js/console.log "creating new orgpad on server" (-> state .-datom .-db) db))
                _ (net/send-cmd {:action :orgpad.server/create-orgpad
                                 :params {:atom (-> state .-atom (select-keys [:app-state]))
                                          :db (dt/write-transit-str db)}})
                data (<! (discard-all-but res-chan is-response?))
                u (url/url (aget js/window "location" "href"))]
            (when ocfg/*online-debug* (js/console.log "orgpad created" data))
            (when (not= (get-in data [:?data 1 :result]) :orgpad.server/error)
              (aset js/window "location" "href"
                    (str (assoc-in u [:query "o"] (get-in data [:?data 1 :result :orgpad.server/uuid])))))))))))

(defn update!
  [uuid changes]
  (when (and uuid (or (:atom changes) (-> changes :db seq)))
    (net/send-cmd {:action :orgpad.server/update-orgpad
                   :params (assoc changes
                                  :orgpad.server/uuid uuid)})))
