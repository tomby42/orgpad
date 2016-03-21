(ns ^{:doc "Desc editor"}
  orgpad.components.atomic.desc-editor
  (:require [rum.core :as rum]
            [orgpad.cycle.life :as lc]))


(rum/defcc desc-editor < rum/static lc/parser-type-mixin-context [component id desc]
  [ :div { :className "react-tagsinput" }
   [ :input { :value desc
              :onChange (fn [e]
                          (lc/transact! component [[:desc/update { :db/id id
                                                                   :orgpad/desc (-> e .-target .-value) } ] ] ) )
              :className "react-tagsinput-input" } ] ] )
