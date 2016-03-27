(ns ^{:doc "Atom editor"}
  orgpad.components.atomic.atom-editor
  (:require [rum.core :as rum]
            [orgpad.cycle.life :as lc]
            [cljsjs.react-tinymce]))


(defn local
  "Adds an atom to component’s state that can be used as local state.
   Atom is stored under key `:rum/local`.
   Component will be automatically re-rendered if atom’s value changes"
  [initial & [key]]
  (let [key (or key :rum/local)]
    { :transfer-state
      (fn [old new]
        (assoc new key (old key)))
      :will-mount
      (fn [state]
        (let [local-state (volatile! initial)
              component   (:rum/react-component state)]
          (assoc state key local-state))) }))


(def local-static
  { :should-update
   (fn [old-state new-state]
     (let [old-args (butlast (:rum/args old-state))
           new-args (butlast (:rum/args new-state))]
       (or (not= old-args new-args)
           (not= (-> new-state :rum/local deref) (last (:rum/args new-state)) )) ))
   })

(rum/defcc atom-editor < local-static (local nil) lc/parser-type-mixin-context [component id atom]
  [ :div {}
    (.createElement
     js/React
     js/ReactTinymce
     #js { :content atom
           :config #js { :inline true
                         :plugins "autolink link image lists print preview code"
                         :toolbar "undo redo | bold italic | alignleft aligncenter alignright"
                        }
           :onChange (fn [e]
                       (let [val (-> e .-target .getContent)]
                         (vreset! (-> component rum/state deref :rum/local) val)
                         (lc/transact!
                          component
                          [[:orgpad.atom/update
                            { :db/id id
                              :orgpad/atom val } ]] )))
          }
     nil) ] )
