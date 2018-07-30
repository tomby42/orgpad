(ns ^{:doc "App life cycle functionality"}
  orgpad.cycle.life
  (:require [rum.core :as rum]
            [orgpad.core.store :as store]
            [orgpad.cycle.effects :as eff]
            [orgpad.cycle.parser :as parser]
            [orgpad.tools.jcolls :as jcolls :refer [aget-safe]]))

(defn- bind-atom
  [state-atom & [key]]
  (let [key (or key :orgpad.cycle/bind-atom)]
    { :will-mount
      (fn [state]
        (let [component   (:rum/react-component state)]
          (add-watch state-atom key
                     (fn [_ _ _ _]
                       (rum/request-render component)
                       ))
          state))

      :will-unmount
      (fn [state]
        (remove-watch state-atom key)
        state)
     }))

(def parser-type-mixin
  { :class-properties { :childContextTypes { :parser-read js/React.PropTypes.func
                                             :parser-mutate js/React.PropTypes.func
                                             :global-conf js/React.PropTypes.array
                                             :global-cache js/React.PropTypes.object } } })

(def parser-type-mixin-context
  { :class-properties { :contextTypes { :parser-read js/React.PropTypes.func
                                        :parser-mutate js/React.PropTypes.func
                                        :global-conf js/React.PropTypes.array
                                        :global-cache js/React.PropTypes.object } } })

(defn- parser-mixin
  [state parser-state read-fn mutate-fn update-fn global-cfg]

  (let [force-update-all (volatile! false)
        force-update-part (volatile! {})
        global-cache #js {}]
    (letfn [(parser-stack-info [key params]
              (-> @parser-state
                  (get-in [:stack [key params]])
                  (->> (map #(aget % "value")))))

            (parser-read [key params disable-cache?]
              (let [pstate-cur (@parser-state [key params])]
                (if (nil? pstate-cur)
                  (let [pstate (parser/parse-query {:state @state
                                                    :read read-fn
                                                    :parser-stack-info parser-stack-info
                                                    :global-cache global-cache} key params)]
                    (when (not disable-cache?)
                      (vswap! parser-state assoc [key params] pstate))
                    (aget pstate "value"))
                  (aget pstate-cur "value"))))

            (parser-state-push! [key params]
              ;; (println (@parser-state [key params]))
              (vswap! parser-state update-in [:stack [key params]] (fnil conj [])
                      (parser/clone-node (@parser-state [key params]))))

            (parser-state-pop! [key params update!]
              ;; (println (last (get-in @parser-state [:stack [key params]])))
              (let [current-state (get @parser-state [key params])
                    old-state (-> @parser-state (get-in [:stack [key params]]) peek)]
                (when update!
                  (update! old-state current-state)
                  (vswap! parser-state assoc [key params] old-state))
                (vswap! parser-state update-in [:stack [key params]] pop)))

            (parser-mutate [key-params-tuple]
              (let [env {:global-cache global-cache
                         :force-update! (partial parser/force-update! force-update-all force-update-part)
                         :parser-state-push! parser-state-push!
                         :parser-state-pop! parser-state-pop!
                         :parser-stack-info parser-stack-info
                         :transact! parser-mutate }
                    [new-store key-params-read effects]
                    (reduce
                     (fn [[store key-params-read effects] [key params & [type] :as kp]]
                       (if (= type :read)
                         [store (conj key-params-read kp) effects]
                         (let [{:keys [state effect]}
                               (mutate-fn (assoc env :state store) key params)]
                           [(or state store) key-params-read
                            (if (nil? effect)
                              effects
                              (conj effects effect))])))
                     [@state [] []] (conj key-params-tuple [:orgpad/log @state]))

                    key-params-read' (if (empty? key-params-read)
                                       (-> @parser-state (dissoc :stack) keys)
                                       key-params-read)]

                (doseq [[key params] key-params-read']
                  (let [pstate-cur (@parser-state [key params])
                        pstate (parser/update-parsed-query
                                new-store read-fn pstate-cur update-fn
                                force-update-all force-update-part
                                global-cache)]
                    (vswap! parser-state assoc [key params] pstate)))

                (reset! state (store/reset-changes new-store))

                (eff/do-effects effects) ))]

      { :child-context
        (fn [_]
          { :parser-read
            parser-read

            :parser-mutate
            parser-mutate

            :global-conf
            #js [global-cfg]

            :global-cache
            global-cache }) })))

(defn- container-mixin
  [component]
  { :render (fn [state] [(component) state]) })

(defn- create-root-class
  [init-store read-fn mutate-fn update-fn root-component global-cfg]
  (let [state (atom init-store)
        parser-state (volatile! {})
        context (parser-mixin state parser-state read-fn mutate-fn update-fn global-cfg)
        class (rum/build-class (fn [state] [(root-component) state])
                               [(bind-atom state)
                                context
                                parser-type-mixin
                                parser-type-mixin-context]
                               "Orgpad root component")]
    [class ((context :child-context))]))

(defn create-cycle
  "Creates app life cycle infrastrucure "
  [initial-store read-fn mutate-fn update-fn root-el root-component global-cfg]

  (let [[class context] (create-root-class initial-store read-fn mutate-fn update-fn root-component global-cfg)
        el    (rum/element class {} nil)]
    (rum/mount el root-el)
    (assoc context :root-el el)))

(defn query
  "Returns value of query for given 'component', 'key' and
  'params'. optional 'disable-cache?' switches off cache of results."
  [component key params & [disable-cache?]]
  (let [parser-read (aget (.. component -context) "parser-read")]
    (assert (-> parser-read nil? not))
    (parser-read key params disable-cache?)))

(defn transact!
  "Updates state regarding to 'component' and 'key-params-tuple'"
  [component key-params-tuple]
  (let [parser-mutate (aget (.. component -context) "parser-mutate")]
    (assert (-> parser-mutate nil? not))
    (parser-mutate key-params-tuple)))

(defn global-conf
  "Returns global configuration binded to this life cycle"
  [component]
  (aget (.. component -context) "global-conf" 0))

(defn set-global-cache
  "Sets global cache entry"
  ([component key val]
   (let [global-cache (aget (.. component -context) "global-cache")]
     (assert (-> global-cache nil? not))
     (aset global-cache key val)))

  ([component key1 key2 & kv]
   (let [global-cache (aget (.. component -context) "global-cache")]
     (assert (-> global-cache nil? not))
     (apply jcolls/aset! global-cache key1 key2 kv))))

(defn get-global-cache
  "Gets global cache entry"
  ([component]
   (let [global-cache (aget (.. component -context) "global-cache")]
     (assert (-> global-cache nil? not))
     global-cache))

  ([component key]
   (let [global-cache (aget (.. component -context) "global-cache")]
     (assert (-> global-cache nil? not))
     (aget-safe global-cache key)))

  ([component key & keys]
   (let [global-cache (aget (.. component -context) "global-cache")]
     (assert (-> global-cache nil? not))
     (apply aget-safe global-cache key keys))))
