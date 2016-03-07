(ns ^{:doc "Tags editor"}
  orgpad.components.atomic.tags-editor
  (:require
   [om.next :as om :refer-macros [defui]]
   [om.dom :as dom]
   [cljsjs.react-tagsinput]))

(defui TagsEditor

  Object

  (render
   [this]
   (let [{:keys [tags id on-change]} (om/props this)]
     (dom/div
      nil
      (.createElement js/React
                      js/ReactTagsInput
                      #js {:value (clj->js (or tags []))
                           :onlyUnique true
                           :onChange on-change
                           }
                      nil)
      ))))

(def tags-editor (om/factory TagsEditor))
