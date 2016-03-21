(ns ^{:doc "Atom editor"}
  orgpad.components.atomic.atom-editor
  (:require [rum.core :as rum]
            [orgpad.cycle.life :as lc]
            [cljsjs.react-tinymce]))


(rum/defcc atom-editor < rum/static lc/parser-type-mixin-context [component id atom]
  [ :div {}
    (.createElement js/React
                    js/ReactTinymce
                    #js { :content atom
                          :config #js { :inline true
                                        :plugins "autolink link image lists print preview"
                                        :toolbar "undo redo | bold italic | alignleft aligncenter alignright"
                                      }
                         }
                    nil) ] )
