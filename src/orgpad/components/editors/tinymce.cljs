(ns ^{:doc "Tinymce editor component"}
  orgpad.components.editors.tinymce
  (:require [rum.core :as rum]
            [cljsjs.react-tinymce]))

(def default-config-full
  #js {:inline false
       :auto_focus true
       :theme "modern"
       :plugins "advlist autolink autoresize lists link image charmap print preview hr anchor pagebreak
                 searchreplace wordcount visualblocks visualchars code fullscreen
                 insertdatetime media nonbreaking save table contextmenu directionality
                 emoticons template paste textcolor colorpicker textpattern imagetools codesample toc"
       :toolbar "undo redo | bold italic underline strikethrough | alignleft aligncenter alignright alignjustify |  bullist numlist outdent indent | formatselect fontselect fontsizeselect | forecolor backcolor | codesample"
       :autoresize_max_height 600
       :paste_data_images true})


(def default-config-quick
  #js {:inline false
       :auto_focus true
       :height 250
       :menubar false
       :theme "modern"
       :plugins "advlist autolink  lists link image charmap print preview hr anchor pagebreak
                 searchreplace wordcount visualblocks visualchars fullscreen
                 insertdatetime media nonbreaking table contextmenu directionality
                 emoticons paste textcolor colorpicker textpattern imagetools toc"
       :toolbar "bold italic underline | alignleft aligncenter alignright alignjustify | bullist numlist outdent indent"
       :paste_data_images true})

(def default-config-simple-inline
  #js {:inline true
       :menubar false
       :toolbar false
       :auto_focus true})

(rum/defc tinymce < rum/static
  [content on-change & [cfg]]
  (js/React.createElement
   js/ReactTinymce
   #js {:content content
        :config (or cfg default-config-full)
        :onChange on-change}
   nil))
