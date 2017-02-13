(ns ^{:doc "App life cycle functionality"}
  orgpad.cycle.life
  (:require [rum.core :as rum]
            [orgpad.core.store :as store]
            [orgpad.cycle.parser :as parser]
            [orgpad.cycle.effects :as eff]
            [orgpad.tools.jcolls :as jcolls]))

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
    (letfn [(parser-read [key params disable-cache?]
              (let [pstate-cur (@parser-state [key params])]
                (if (nil? pstate-cur)
                  (let [pstate (parser/parse-props @state read-fn global-cache key params)]
                    (when (not disable-cache?)
                      (vswap! parser-state assoc [key params] pstate))
                    (aget pstate "value"))
                  (aget pstate-cur "value"))))

            (parser-mutate [key-params-tuple]
              (let [[new-store key-params-read effects]
                    (reduce
                     (fn [[store key-params-read effects] [key params & [type] :as kp]]
                       (if (= type :read)
                         [store (conj key-params-read kp) effects]
                         (let [{:keys [state effect]}
                               (mutate-fn { :state store
                                            :global-cache global-cache
                                            :force-update! (partial parser/force-update! force-update-all force-update-part)
                                            :transact! parser-mutate } key params)]
                           [state key-params-read
                            (if (nil? effect)
                              effects
                              (conj effects effect))] )))
                     [@state [] []] key-params-tuple)

                    key-params-read' (if (empty? key-params-read)
                                       (keys @parser-state)
                                       key-params-read)]

                (doseq [[key params] key-params-read']
                  (let [pstate-cur (@parser-state [key params])
                        pstate (parser/update-parsed-props
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
        class (rum/build-class [(container-mixin root-component)
                                (bind-atom state)
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

(defn props
  "Returns unwinded value for given 'component', 'key' and
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
     (aget global-cache key)))

  ([component key & keys]
   (let [global-cache (aget (.. component -context) "global-cache")]
     (assert (-> global-cache nil? not))
     (apply aget global-cache key keys))))
