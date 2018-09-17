(ns ^{:doc "Global settings"}
  orgpad.components.root.settings
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.cycle.life :as lc]
            [orgpad.tools.dscript :as ds]
            [orgpad.tools.orgpad :as ot]
            [orgpad.tools.orgpad-manipulation :as omt]
            [orgpad.tools.js-events :as jev]
            [orgpad.tools.rum :as trum]
            [orgpad.tools.dom :as dom]))

(rum/defcc settings < lc/parser-type-mixin-context
  [component app-state on-close]
  [:div.settings
   [:div.header
    [:div.label "Settings"]
    [:div.close {:on-click on-close}
     [:span.far.fa-times-circle]]]

   [:div.block
    [:div.label "Basic"]
    [:div.paragraph
     [:div.line
      [:div.left "Orgpad Title"]
      [:div.right [:input.orgpad-name {:type "text"
                                       :placeholder "Write a title"
                                       :value (or (:orgpad-name app-state) "")
                                       :onChange #(let [name (-> % .-target .-value)]
                                                    (dom/set-el-text (dom/ffind-tag :title) name)
                                                    (lc/transact! component
                                                                  [[:orgpad/app-state [[:orgpad-name] name]]]))}]]]
     [:div.line
      [:div.left "Orgpad Filename"]
      [:div.right [:input.orgpad-name {:type "text"
                                       :placeholder "Write a filename"
                                       :value (or (:orgpad-filename app-state) "")
                                       :onChange #(let [filename (-> % .-target .-value)]
                                                    (lc/transact! component
                                                                  [[:orgpad/app-state [[:orgpad-filename] filename]]]))}]]]
     [:div.line
      [:div.left "Compress Saved Files"]
      [:div.right
       [:input (merge {:type "checkbox"
                       :onChange #(lc/transact! component
                                                [[:orgpad/app-state [[:compress-saved-files?]
                                                                     (-> % .-target .-checked)]]])}
                      (if (:compress-saved-files? app-state)
                        {:checked true} {}))]]]]] [:div.block
                                                   [:div.label "Advanced"]
                                                   [:div.paragraph
                                                    [:div.line
                                                     [:div.left "Enable Experimental Features"]
                                                     [:div.right [:input (merge {:type "checkbox"
                                                                                 :onChange #(lc/transact! component
                                                                                                          [[:orgpad/app-state [[:enable-experimental-features?]
                                                                                                                               (-> % .-target .-checked)]]])}
                                                                                (if (:enable-experimental-features? app-state)
                                                                                  {:checked true} {}))]]]]]])
