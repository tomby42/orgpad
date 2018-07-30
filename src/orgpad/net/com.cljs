(ns orgpad.net.com
  (:require
   [clojure.string  :as str]
   [cljs.core.async :as async  :refer (<! >! put! chan close! put!)]
   [taoensso.encore :as encore :refer-macros (have have?)]
   [taoensso.timbre :as timbre :refer-macros (tracef debugf infof warnf errorf)]
   [taoensso.sente  :as sente  :refer (cb-success?)]
   [cemerick.url :as url]

   ;; Optional, for Transit encoding:
   [taoensso.sente.packers.transit :as sente-transit]))

(defonce ^:private sente-client (atom nil))

(defn- init-sente [u]
  (let [;; Serializtion format, must use same val for client + server:
        ;; packer :edn ; Default packer, a good choice in most cases
        packer (sente-transit/get-transit-packer) ; Needs Transit dep
        u' (url/url u)
        info
        (sente/make-channel-socket-client!
         (:path u') ; Must match server Ring routing URL
         {:type   :auto
          :host (str (:host u') (if (= (:port u') -1) "" (str ":" (:port u'))))
          :packer packer})]
    info))

(defn send-cmd
  [cmd]
  (js/console.log "send-cmd" cmd)
  (let [chsk-send! (:send-fn @sente-client)]
    (chsk-send! [:orgpad.server/cmd cmd] 5000
                (fn [cb-reply]
                  (js/console.log "Update successfuly sent:" cb-reply)))))

;;;; Sente event handlers

(defmulti -event-msg-handler
  "Multimethod to handle Sente `event-msg`s"
  :id ; Dispatch on event-id
  )

(defn event-msg-handler
  "Wraps `-event-msg-handler` with logging, error catching, etc."
  [{:as ev-msg :keys [id ?data event]}]
  (-event-msg-handler ev-msg)
  (when-let [ch (:res-chan @sente-client)]
    (put! ch ev-msg)))

(defmethod -event-msg-handler
  :default ; Default/fallback case (no other matching handler)
  [{:as ev-msg :keys [event]}]
  (js/console.log "Unhandled event:" event))

(defmethod -event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (let [[old-state-map new-state-map] (have vector? ?data)]
    (if (:first-open? new-state-map)
      (do
        (js/console.log "Channel socket successfully established!:" new-state-map)
        (swap! sente-client assoc :connected? true))
      (js/console.log "Channel socket state change:" new-state-map))))

(defmethod -event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (js/console.log "Push event from server: %s" ?data)
  (let [[evt data] ?data]
    (-event-msg-handler {:id evt :?data data})))

(defmethod -event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (js/console.log "Handshake: %s" (prn ?data))))

(defmethod -event-msg-handler :orgpad.server/response
  [{:as ev-msg :keys [?data]}]
  (js/console.log "Response event from server:" ev-msg))

;;;; Sente event router (our `event-msg-handler` loop)

(defn- stop-router! []
  (when-let [stop-f (:router_ @sente-client)]
    (stop-f))
  (when-let [ch (:res-chan @sente-client)]
    (close! ch)))

(defn- start-router! []
  (stop-router!)
  (swap! sente-client assoc
         :router_
         (sente/start-client-chsk-router!
          (:ch-recv @sente-client) event-msg-handler)
         :res-chan (chan 1000)))

;;;; Init stuff

(defn start! [url]
  (let [info (init-sente url)]
    (reset! sente-client info)
    (start-router!)))

(defn is-online?
  []
  (and @sente-client
       (:connected? @sente-client)))
