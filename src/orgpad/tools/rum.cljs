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
