(ns ^{:doc "App life cycle functionality"}
  orgpad.cycle.life
  (:require [rum.core :as rum]
            [orgpad.core.store :as store]
            [orgpad.cycle.parser :as parser]))

(defn- bind-atom
  [state-atom & [key]]
  (let [key (or key :orgpad.cycle/bind-atom)]
    { :will-mount
      (fn [state]
        (let [component   (:rum/react-component state)]
          (add-watch state-atom key
                     (fn [_ _ _ _]
                       (rum/request-render component)))
          state))

      :will-unmount
      (fn [state]
        (remove-watch state-atom key)
        state)
     }))

(def parser-type-mixin
  { :class-properties { :childContextTypes { :parser-read js/React.PropTypes.func
                                             :parser-mutate js/React.PropTypes.func } } })

(def parser-type-mixin-context
  { :class-properties { :contextTypes { :parser-read js/React.PropTypes.func
                                        :parser-mutate js/React.PropTypes.func } } })

(defn- parser-mixin
  [state parser-state read-fn mutate-fn update-fn]

  (letfn [(parser-read [key params]
            (let [pstate-cur (@parser-state [key params])]
              (if (nil? pstate-cur)
                (let [pstate (parser/parse-props @state read-fn key params)]
                  (vswap! parser-state assoc [key params] pstate)
                  (pstate :value))
                (pstate-cur :value))))

          (parser-mutate [key-params-tuple]
            (let [[new-store key-params-read]
                  (reduce (fn [[store key-params-read] [key params & [type] :as kp]]
                            (if (= type :read)
                              [store (conj key-params-read kp)]
                              [(mutate-fn { :state store
                                            :transact! parser-mutate } key params)
                               key-params-read] ))
                          [@state []] key-params-tuple)

                  key-params-read' (if (empty? key-params-read)
                                     (keys @parser-state)
                                     key-params-read)]

              (doseq [[key params] key-params-read']
                (let [pstate-cur (@parser-state [key params])
                      pstate (parser/update-parsed-props
                              new-store read-fn pstate-cur update-fn)]
                  (vswap! parser-state assoc [key params] pstate)))
              (reset! state (store/reset-changes new-store)) ))]

    { :child-context
      (fn [_] 
        { :parser-read
          parser-read

          :parser-mutate
          parser-mutate }) }))

(defn- container-mixin
  [component]
  { :render (fn [state] [(component) state]) })

(defn- create-root-class
  [init-store read-fn mutate-fn update-fn root-component]
  (let [state (atom init-store)
        parser-state (volatile! {})
        class (rum/build-class [(container-mixin root-component)
                                (bind-atom state)
                                (parser-mixin state parser-state read-fn mutate-fn update-fn)
                                parser-type-mixin]
                               "Orgpad root component")]
    class))

(defn create-cycle
  [initial-store read-fn mutate-fn update-fn root-el root-component]

  (let [class (create-root-class initial-store read-fn mutate-fn update-fn root-component)
        el    (rum/element class {} nil)]
    (rum/mount el root-el)))

(defn props
  [component key params]
  (let [parser-read (aget (.. component -context) "parser-read")]
    (assert (-> parser-read nil? not))
    (parser-read key params)))

(defn transact!
  [component key-params-tuple]
  (let [parser-mutate (aget (.. component -context) "parser-mutate")]
    (assert (-> parser-mutate nil? not))
    (parser-mutate key-params-tuple)))
