(ns ^{:doc "RUM tools"}
  orgpad.tools.rum
  (:require [rum.core :as rum]
            [orgpad.tools.colls :as colls]))

(defn comp->local-state
  ([component]
   (-> component rum/state deref :rum/local))
  ([component non-reactive?]
   (if non-reactive?
     (-> component rum/state deref :rum/no-reactive-local)
     (-> component rum/state deref :rum/local))))

(defn comp->args
  [component]
  (-> component rum/state deref :rum/args))

(defn component
  [state]
  (state :rum/react-component))

(defn args
  [state]
  (state :rum/args))

(defn gen-update-mixin
  [update-fn]
  {:did-mount
   (fn [state]
     (update-fn state)
     state)

   :did-update
   (fn [state]
     (update-fn state)
     state)})

(defn gen-reg-mixin
  [reg-fn unreg-fn]
  {:did-mount
   (fn [state]
     (reg-fn state))

   :will-unmount
   (fn [state]
     (unreg-fn state))})

(defn ref
  "Given state and ref handle, returns React component"
  [state key]
  (-> state :rum/react-component (aget "refs") (aget (name key))))

(defn ref-node
  "Given state and ref handle, returns DOM node associated with ref"
  [state key]
  (js/ReactDOM.findDOMNode (ref state (name key))))

(def istatic
  "Mixin. Will avoid re-render if none of component’s arguments have changed.
   Does equality check (identical?) on all arguments"
  {:should-update
   (fn [old-state new-state]
      ;; (when (not (colls/shallow-eq
      ;;             (:rum/args old-state) (:rum/args new-state)))
      ;;   (println "should-update")
      ;;   (println "old" (:rum/args old-state))
      ;;   (println "new" (:rum/args new-state)))
        ;; (println "eq" (colls/shallow-eq
        ;;               (:rum/args old-state) (:rum/args new-state)))
     (not
      (colls/shallow-eq
       (:rum/args old-state) (:rum/args new-state))))})

(defn statical
  "Create mixin. Will avoid re-render if none of component’s arguments have changed.
   Does equality check (depends on feeded vector of equality functions) on all arguments"
  [eq-fns]
  {:should-update
   (fn [old-state new-state]
      ;; (when (not
      ;;        (colls/semi-shallow-eq
      ;;         (:rum/args old-state) (:rum/args new-state) eq-fns))
      ;;   (println "should-update")
      ;;   (println "old" (:rum/args old-state))
      ;;   (println "new" (:rum/args new-state)))

     (let [new-args (:rum/args new-state)
           old-args (:rum/args old-state)]
       (or
        (empty? old-args)
        (empty? new-state)
        (not
         (colls/semi-shallow-eq
          old-args new-args eq-fns)))))})

(defn force-update
  [comp]
  (when (.isMounted comp)
    (.forceUpdate comp)))

(defn no-reactive-local
  ([initial] (no-reactive-local initial :rum/no-reactive-local))
  ([initial key]
   {:will-mount
    (fn [state]
      (let [local-state (volatile! (if (fn? initial)
                                     (initial)
                                     initial))]
        (assoc state key local-state)))}))
