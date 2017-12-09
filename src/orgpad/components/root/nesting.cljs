(ns ^{:doc "Nesting status bar"}
  orgpad.components.root.nesting
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [cljsjs.react-select]
            [orgpad.cycle.life :as lc]
            [orgpad.tools.dscript :as ds]
            [orgpad.components.registry :as registry]
            [orgpad.components.menu.toolbar :as tbar]
            [orgpad.tools.orgpad :as ot]
            [orgpad.tools.orgpad-manipulation :as omt]
            [orgpad.tools.js-events :as jev]
            [orgpad.tools.rum :as trum]))

;; Input format for nesting data
;; =============================
;;
;; List of units in the nesting, each unit is described by a map
;; {:id       ...   unit id
;;  :icon     ...   unit icon
;;  :title    ...   unit title
;;  :pages    ...   unit pages current/total for Book view }

(defn- gen-unit-button
  [component {:keys [id icon title pages]}]
  (let [icon-span [:i { :key (str id "-icon") :className (str icon " fa-lg fa-fw") }]
        label-span [:span { :key (str id "-label") :className "label" } title]]
    [:span.btn
      {:key id
       ;;:onClick #(js/console.log "id 0:"  (lc/query component :orgpad/unit-view {:id 0})) ;; needs more parameters
       }
      icon-span label-span]))

(defn- gen-unit-list
  [component data]
  (let [sep-data (map #(identity
                          [:span.sep {:key (str (:id %) "-sep") }
                            [:i.far.fa-chevron-right.fa-lg.fa-fw]]) data)]
    (drop-last (interleave 
      (map (partial gen-unit-button component) data)
      sep-data))))

(rum/defcc nesting < lc/parser-type-mixin-context
  [component {:keys [unit view path-info] :as unit-tree}]
  [:div.nesting
    (js/console.log "Nesting: " (lc/query component :orgpad/root-view-stack-info [:orgpad/root-view []]))
    (gen-unit-list component 
      [{:id "root" :icon "far fa-share-alt" :title "Root component"}
       {:id "next" :icon "far fa-book" :title "Book view"}
       {:id "last" :icon "far fa-share-alt" :title "Map view"}
       {:id "next2" :icon "far fa-book" :title "Book view"}
       {:id "last2" :icon "far fa-share-alt" :title "Map view"}
       {:id "next3" :icon "far fa-book" :title "Book view"}
       {:id "last3" :icon "far fa-share-alt" :title "Map view"}])
   ])
