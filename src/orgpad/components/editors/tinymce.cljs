(ns ^{:doc "Tinymce editor component"}
  orgpad.components.editors.tinymce
  (:require [rum.core :as rum]
            [cljsjs.react-tinymce]))

(def default-config-full
  #js { :inline false
        :theme "modern"
        :plugins "advlist autolink autoresize lists link image charmap print preview hr anchor pagebreak
                                   searchreplace wordcount visualblocks visualchars code fullscreen
                                   insertdatetime media nonbreaking save table contextmenu directionality
                                   emoticons template paste textcolor colorpicker textpattern imagetools codesample toc"
        :toolbar "undo redo | bold italic | alignleft aligncenter alignright |  bullist numlist outdent indent | fontselect fontsizeselect | forecolor backcolor | codesample"
       })

(def default-config-simple-inline
  #js { :inline true
        :menubar false
        :toolbar false })

(rum/defc tinymce < rum/static
  [content on-change & [cfg]]
  (js/React.createElement
     js/ReactTinymce
     #js { :content content
           :config (or cfg default-config-full)
           :onChange on-change }
     nil))
