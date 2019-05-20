(ns ^{:doc "Desc editor"}
  orgpad.components.atomic.desc-editor
  (:require [rum.core :as rum]
            [orgpad.cycle.life :as lc]))

(rum/defcc desc-editor < rum/static lc/parser-type-mixin-context
  [component id view desc]
  [:div {:className "react-tagsinput desc-editor"}
   [:input {:value (or desc "")
            :type "text"
            :placeholder "Write a description"
            :onChange (fn [e]
                        (lc/transact!
                         component
                         [[:orgpad.desc/update
                           {:db/id id
                            :orgpad/view view
                            :orgpad/desc (-> e .-target .-value)}]]))}]])
