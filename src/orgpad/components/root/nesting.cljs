(ns ^{:doc "Nesting status bar"}
  orgpad.components.root.nesting
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [cljsjs.react-select]
            [orgpad.cycle.life :as lc]
            [orgpad.tools.dscript :as ds]
            [orgpad.components.registry :as registry]
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
;;  :label    ...   unit label
;;  :title    ...   unit tooltip help
;;  :on-click ...   function to be called when clicked }

(defn- gen-nesting-button
  "Generates nesting button for one unit from data."
  [{:keys [id icon label title on-click]}]
  (let [icon-span [:i {:key (str id "-icon") :className (str icon " fa-lg fa-fw")}]
        label-span [:span {:key (str id "-label") :className "label"} label]]
    [:span.btn
     {:key id
      :title title
      :onClick on-click}
     icon-span label-span]))

(defn- gen-nesting-list
  "Generates list of nesting buttons from data, with inserted separators."
  [data]
  (let [sep-data (map #(identity
                        [:span.sep {:key (str (:id %) "-sep")}
                         [:i.far.fa-chevron-right.fa-lg.fa-fw]]) data)]
    (drop-last (interleave
                (map gen-nesting-button data)
                sep-data))))

(defn- close-unit
  [component n]
  (doseq [_ (range n)]
    (lc/transact! component [[:orgpad/root-unit-close {}]])))

(defn- gen-unit-data
  "Creates data for one unit from its unit-tree where jump-up is the number of units we need to close to get back to this unit."
  [component {:keys [unit view path-info] :as unit-tree} jump-up]
  (let [id (:db/id unit)
        view-type (ot/view-type unit-tree)
        view-info (-> view :orgpad/view-type registry/get-component-info)
        view-name (:orgpad/view-name view-info)
        icon (:orgpad/view-icon view-info)
        label (str id
                   (when (= view-type :orgpad/map-tuple-view)
                     (str " (" (ot/sheets-to-str unit-tree) ")")))]
    {:id id
     :icon icon
     :label label
     :title view-name
     :on-click #(close-unit component jump-up)}))

(rum/defcc nesting < lc/parser-type-mixin-context
  "Nesting status bar component."
  [component {:keys [unit view path-info] :as unit-tree}]
  (let [unit-stack (concat
                    (lc/query component :orgpad/root-view-stack-info [:orgpad/root-view []] {:disable-cache? true})
                    [unit-tree])
        level (dec (count unit-stack))
        jumps-up (range level -1 -1)]
    (when (> (count unit-stack) 1)
      [:div.nesting
       (gen-nesting-list (map (partial gen-unit-data component) unit-stack jumps-up))])))
