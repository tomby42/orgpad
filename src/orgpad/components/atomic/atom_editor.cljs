(ns ^{:doc "Atom editor"}
  orgpad.components.atomic.atom-editor
  (:require [rum.core :as rum]
            [orgpad.tools.rum :as trum]
            [orgpad.cycle.life :as lc]
            [cljsjs.react-tinymce]))

(def unit-change
  { :should-update
    (fn [old-state new-state]
      (or
       (not= (-> old-state :rum/args first)
             (-> new-state :rum/args first))
       (not= (-> old-state :rum/args second :orgpad/view-name)
             (-> new-state :rum/args second :orgpad/view-name))
       )) })

(rum/defcc atom-editor < unit-change lc/parser-type-mixin-context
  [component unit-id view atom]
  [ :div { :key (str unit-id (view :orgpad/view-name)) }
    (.createElement
     js/React
     js/ReactTinymce
     #js { :content atom
           :config #js { :inline false
                         :theme "modern"
                         :plugins "advlist autolink autoresize lists link image charmap print preview hr anchor pagebreak
                                   searchreplace wordcount visualblocks visualchars code fullscreen
                                   insertdatetime media nonbreaking save table contextmenu directionality
                                   emoticons template paste textcolor colorpicker textpattern imagetools codesample toc"
                         :toolbar "undo redo | bold italic | alignleft aligncenter alignright |  bullist numlist outdent indent | fontselect fontsizeselect | forecolor backcolor | codesample"
                        }
           :onChange (fn [e]
                       (let [target (aget e "target")]
                         (lc/transact!
                          component
                          [[:orgpad.atom/update
                            { :db/id unit-id
                              :orgpad/view (-> component rum/state deref :rum/args second)
                              :orgpad/atom (.call (aget target "getContent") target) } ]] )))
          }
     nil) ] )
