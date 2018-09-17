(ns ^{:doc "Tags editor"}
  orgpad.components.atomic.tags-editor
  (:require [rum.core :as rum]
            [clojure.set :as s]
            [orgpad.cycle.life :as lc]
            [cljsjs.react-tagsinput]))

(rum/defcc tags-editor < rum/static lc/parser-type-mixin-context
  [component id view tags]
  [:div {:className "tags-editor"}
   (.createElement js/React
                   js/ReactTagsInput
                   #js {:value (clj->js (or tags []))
                        :onlyUnique true
                        :inputProps #js {:className "react-tagsinput-input"
                                         :placeholder "Write a tag"}
                        :onChange (fn [new-tags]
                                    (let [new-tags-set       (set new-tags)
                                          old-tags-set       (set tags)
                                          removed-tags       (s/difference old-tags-set new-tags-set)
                                          added-tags         (s/difference new-tags-set old-tags-set)]
                                      (if (-> removed-tags empty? not)
                                        (lc/transact!
                                         component
                                         [[:orgpad.tags/remove
                                           {:db/id id
                                            :orgpad/view view
                                            :orgpad/tags removed-tags}]])
                                        (if (-> added-tags empty? not)
                                          (lc/transact!
                                           component
                                           [[:orgpad.tags/add
                                             {:db/id id
                                              :orgpad/view view
                                              :orgpad/tags added-tags}]])))))}
                   nil)])
