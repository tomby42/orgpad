(ns ^{:doc "Atom editor"}
  orgpad.components.atomic.atom-editor
  (:require [rum.core :as rum]
            [orgpad.tools.rum :as trum]
            [orgpad.cycle.life :as lc]
            [cljsjs.react-tinymce]))

(def unit-change
  { :should-update
   (fn [old-state new-state]
     (not= (-> old-state :rum/args first) (-> new-state :rum/args first))) })

(rum/defcc atom-editor < unit-change lc/parser-type-mixin-context
  [component unit-id view atom]
  [ :div { :key unit-id }
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
                         (lc/transact!
                          component
                          [[:orgpad.atom/update
                            { :db/id unit-id
                              :orgpad/view (-> component rum/state deref :rum/args second)
                              :orgpad/atom val } ]] )))
          }
     nil) ] )
