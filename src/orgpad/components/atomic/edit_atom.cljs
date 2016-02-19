(ns ^{:doc "Atom editor"}
  orgpad.components.atomic.atom-editor
  (:require [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [cljsjs.react-tinymce]))

(defui AtomEditor

  Object
  (render
   [this]
   (let [{:keys [atom id]} (om/props this)]
     (dom/div
      nil
      (.createElement js/React
                      js/ReactTinymce
                      #js {:content atom
                           :config #js {:inline true
                                        :plugins "autolink link image lists print preview"
                                        :toolbar "undo redo | bold italic | alignleft aligncenter alignright"
                                        }
                           }
                      nil)
      ))))

(def atom-editor (om/factory AtomEditor))
