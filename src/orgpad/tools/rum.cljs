(ns ^{:doc "RUM tools"}
  orgpad.tools.rum
  (:require [rum.core :as rum]))

(defn comp->local-state
  [component]
  (-> component rum/state deref :rum/local))

(defn gen-update-mixin
  [update-fn]
  { :did-mount
     (fn [state]
       (update-fn state)
       state)

    :did-update
      (fn [state]
        (update-fn state)
        state) })

(defn gen-reg-mixin
  [reg-fn unreg-fn]
  { :did-mount
     (fn [state]
       (reg-fn state))

    :will-unmount
     (fn [state]
       (unreg-fn state)) })

(defn ref
  "Given state and ref handle, returns React component"
  [state key]
  (-> state :rum/react-component (aget "refs") (aget (name key))))

(defn ref-node
  "Given state and ref handle, returns DOM node associated with ref"
  [state key]
  (js/ReactDOM.findDOMNode (ref state (name key))))

(defn shallow-eq
  [s1 s2]
  (let [n (count s1)]
    (if (== n (count s2))
      (loop [ss1 s1
             ss2 s2
             s   true]
        (if s
          (if (empty? ss1)
            true
            (recur (rest ss1) (rest ss2) (identical? (first ss1) (first ss2))))
          false))
      false)))

(def istatic
  "Mixin. Will avoid re-render if none of component’s arguments have changed.
   Does equality check (identical?) on all arguments"
  { :should-update
    (fn [old-state new-state]
      (not
       (shallow-eq
        (:rum/args old-state) (:rum/args new-state)))) })
