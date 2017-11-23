(ns ^{:doc "Map component"}
  orgpad.components.map.tuple
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.cycle.life :as lc]
            [orgpad.components.registry :as registry]
            [orgpad.components.node :as node]
            [orgpad.components.map.unit :as munit]
            [orgpad.components.map.unit-editor :as uedit]
            [orgpad.tools.time :as t]
            [orgpad.tools.css :as css]
            [orgpad.tools.js-events :as jev]
            [orgpad.tools.rum :as trum]
            [orgpad.tools.orgpad :as ot]
            [orgpad.tools.orgpad-manipulation :as otm]))

(def ^:private CLICK-DELTA 250)

(defn- open-unit
  [component {:keys [unit view]}]
  (uedit/open-unit component
                   (ot/get-sorted-ref unit
                                      (view :orgpad/active-unit))))

(defn- render-local-menu
  [component unit-tree app-state local-state]
  (html
   [ :div { :className "map-tuple-menu" }
    [ :div { :className "tools-menu" :title "Actions" }
     [ :div { :className "tools-button" :onClick #(swap! local-state update-in [:unroll] not) }
      [ :i { :className "fa fa-cogs fa-lg" } ] ]
     [ :div { :className (str "tools" (when (@local-state :unroll) " more-4")) }
      [ :div { :className "tools-button" :title "New sheet"
               :onClick #(otm/new-sheet component unit-tree) }
       [ :i { :className "fa fa-plus-circle fa-lg" } ] ]
      [ :div { :className "tools-button" :title "Previous"
               :onClick #(otm/switch-active-sheet component unit-tree -1) }
       [ :i { :className "fa fa-caret-left fa-lg" } ] ]
      [ :div { :className "tools-button" :title "Next"
               :onClick #(otm/switch-active-sheet component unit-tree 1) }
       [ :i { :className "fa fa-caret-right fa-lg" } ] ]
      [ :div { :className "tools-button" :title "Remove"
               :onClick #(otm/remove-active-sheet component unit-tree) }
       [ :i { :className "fa fa-remove fa-lg" } ] ]
      [ :div { :className "tools-button" :title "Edit" }
       [ :i { :className "fa fa-pencil-square-o fa-lg"
              :onClick #(open-unit component unit-tree)
             } ] ]
      ]
     ] ] ))

(defn- render-sheet-number
  [{ :keys [unit view]}]
  (html
   [ :div { :className "map-tuple-sheet-number" :key 1 }
    (str (-> view :orgpad/active-unit inc) "/" (-> unit :orgpad/refs count))
    ]
  ))

(defn- active-child-tree
  [unit view]
  (let [active-child (-> view :orgpad/active-unit)]
    (-> unit ot/sort-refs (get active-child))))

(defn- render-write-mode
  [component { :keys [unit view props] :as unit-tree } app-state local-state]
  (let [child-tree (active-child-tree unit view)]
    [ :div { :className "map-tuple" }
      (render-local-menu component unit-tree app-state local-state)
      (render-sheet-number unit-tree)
      (when child-tree
        (rum/with-key (node/node child-tree app-state) 2)) ]))

(defn- find-map-tuple-node
  [e]
  (loop [n (.-target e)]
    (if (= (.-className n) "map-tuple")
      n
      (recur (.-parentNode n)))))

(defn- comp-dir
  [e]
  (let [bb (->> e find-map-tuple-node .getBoundingClientRect)
        x (.-clientX e)
        dist->left (- x (.-left bb))
        dist->right (- (.-right bb) x)]
    (if (< dist->right dist->left)
      1
      -1)))

(defn- render-read-mode
  [component { :keys [unit view] :as unit-tree } app-state local-state]
  (let [child-tree (active-child-tree unit view)]
    [ :div { :className "map-tuple"
             :onMouseDown #(swap! local-state merge { :time-stamp (t/now) })
             :onMouseUp (fn [e]
                          (when (and (< (- (t/now) (@local-state :time-stamp)) CLICK-DELTA)
                                     (not= (:mode app-state) :quick-write))
                            (otm/switch-active-sheet component unit-tree (comp-dir e)))) }
     (when child-tree
       (rum/with-key (node/node child-tree app-state) 2))
     [ :div.map-tuple-clicker-left ]
     [ :div.map-tuple-clicker-right ]
     ]))

(rum/defcc map-tuple-component < trum/istatic lc/parser-type-mixin-context
  (rum/local { :unroll false :time-stamp 0} )
  [component unit-tree app-state]
  (let [local-state (trum/comp->local-state component)]
    (if (= (app-state :mode) :write)
      (render-write-mode component unit-tree app-state local-state)
      (render-read-mode component unit-tree app-state local-state))))

(registry/register-component-info
 :orgpad/map-tuple-view
 {
   :orgpad/default-view-info   { :orgpad/view-type :orgpad/map-tuple-view
                                 :orgpad/view-name "default"
                                 :orgpad/active-unit 0 }
   :orgpad/child-default-view-info   { :orgpad/view-type :orgpad/atomic-view
                                       :orgpad/view-name "default" }
   :orgpad/class               map-tuple-component
   :orgpad/needs-children-info true
   :orgpad/view-name           "Notebook View"

   :orgpad/propagate-props-from-children? true
   :orgpad/propagated-props-from-children { :orgpad.map-view/vertex-props
                                             [:orgpad/view-type :orgpad/view-name :orgpad/view-style
                                              :orgpad/unit-width :orgpad/unit-height
                                              :orgpad/unit-border-color :orgpad/unit-bg-color
                                              :orgpad/unit-border-width :orgpad/unit-corner-x
                                              :orgpad/unit-corner-y :orgpad/unit-border-style] }
  })
