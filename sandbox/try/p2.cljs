(ns try.p2
  (:require [orgpad.core.orgpad :as o]
            [orgpad.core.store :as s]
            [orgpad.cycle.parser :as p]))

(defn r1
  [{:keys [state props] :as env} k params]

  (case k
    :a {:a {:b (props env :b params)
            :c (props env :c params)}}
    :b {:b true}
    :c {:c (props env :d params)}
    :d {:d false}))

(defn r2
  [{:keys [state props] :as env} k params]

  (case k
    :a {:a {:b (props env :b params)
            :c (props env :c params)}}
    :b {:b true}
    :c {:c (props env :e params)}
    :d {:d false}
    :e {:e "bleep"}))


(defn changed? 
  [node _]
  (= (node :key) :c))

(defn p2-props []
  (let [t  (p/parse-props {} r1 :a [1 2 3])
        t1 (p/update-parsed-props {} r2 t changed?)]
    t1))
