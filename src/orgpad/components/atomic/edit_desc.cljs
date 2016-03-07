(ns ^{:doc "Desc editor"}
  orgpad.components.atomic.desc-editor
  (:require [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [orgpad.tools.macros :as m :refer-macros [bind]]))

(defui DescEditor

  Object
  (onChange
   [this tags]
   (.log js/console tags)
   )

  (render
   [this]
   (let [{:keys [desc id]} (om/props this)]
     (dom/div 
      #js {:className "react-tagsinput"
           }
      (dom/input #js {:value (or desc "Desc")
                      :onChange (m/bind onChange)
                      :className "react-tagsinput-input"
                      }
                 nil)
      ))))

(def desc-editor (om/factory DescEditor))
