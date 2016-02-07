(ns ^{:doc "Sidebar component"}
  orgpad.components.sidebar.sidebar
  (:require [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]))

(defn- show
  [this]
  (om/set-state! this {:visible true}))

(defn- hide
  [this]
  (om/set-state! this {:visible false}))

(defui SidebarComponent
  Object
  (initLocalState
   [this]
   {:visible false})

  (render
   [this]
   (let [classes (if (-> this om/get-state :visible) "sidebar-visible left" "left")]
     (dom/div
      #js {:className "sidebar"}
      (dom/div
       #js {:className classes}
       [(dom/button
         #js {:type "button"
              :className "sidebar-button"
              :onClick
              (fn [_]
                (if (om/get-state this :visible)
                  (hide this)
                  (show this)))}
         nil)
        (dom/div
         nil
         (-> this .-props .-childrens))]
       ))))

  )

(def sidebar-component (om/factory SidebarComponent))
