(ns orgpad.effects.net
  (:require
   [orgpad.net.com :as net]
   [cljs.core.async :as async :refer (<! >! put! chan close! put! alts! go-loop go)]
   [datascript.core :as d]
   [datascript.transit :as dt]
   [cemerick.url :as url]
   [orgpad.core.store :as store]
   [orgpad.tools.orgpad :as ot]))

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
        _ (js/console.log "transform-db" datoms)
        new-db (-> db
                   :schema
                   d/empty-db
                   (d/with datoms)
                   :db-after)]
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
            _ (js/console.log "Connected from effects")
            _ (net/send-cmd {:action :orgpad.server/exist-orgpad? :params {:orgpad.server/uuid ouid}})
            res (<! (discard-all-but res-chan is-response?))
            _ (js/console.log "exist-orgpad?" res)]
        (if (get-in res [:?data 1 :result])
          (let [_ (net/send-cmd {:action :orgpad.server/connect-to-orgpad
                                 :params {:orgpad.server/uuid ouid}})
                data (<! (discard-all-but res-chan is-response?))]
            (transact! [[:orgpad.net/set-db data]])
            (loop []
              (let [[v ch] (alts! [res-chan in-chan])]
                (when v
                  (recur)))))
          (let [{:keys [db mapping]} (transform-db (-> state .-datom .-db))
                _ (js/console.log "creating new orgpad on server" (-> state .-datom .-db) db)
                _ (net/send-cmd {:action :orgpad.server/create-orgpad
                                 :params {:atom (assoc (.-atom state) :uid->squuid mapping)
                                          :db (dt/write-transit-str db)}})
                data (<! (discard-all-but res-chan is-response?))
                u (url/url (aget js/window "location" "href"))]
            (js/console.log "orgpad created" data)
            (when (not= (get-in data [:?data 1 :result]) :orgpad.server/error)
              (aset js/window "location" "href"
                    (str (assoc-in u [:query "o"] (get-in data [:?data 1 :result :orgpad.server/uuid])))))
            ))))))
