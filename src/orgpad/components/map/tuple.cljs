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
            [orgpad.tools.orgpad-manipulation :as omt]))

(def ^:private CLICK-DELTA 250)

(defn- open-unit
  [component {:keys [unit view]}]
  (omt/open-unit component
                   (ot/get-sorted-ref unit
                                      (view :orgpad/active-unit))))

(defn- render-write-mode
  [component { :keys [unit view props] :as unit-tree } app-state local-state]
  (let [child-tree (ot/active-child-tree unit view)]
    [ :div { :className "map-tuple" }
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
        dist->left (- x (.-left bb))]
    (if (<= dist->left 20)
      -1
      1)))

(defn- render-read-mode
  [component { :keys [unit view] :as unit-tree } app-state local-state]
  (let [child-tree (ot/active-child-tree unit view)]
    [ :div { :className "map-tuple"
             :onMouseDown #(swap! local-state merge { :time-stamp (t/now) })
             :onMouseUp (fn [e]
                          (when (and (< (- (t/now) (@local-state :time-stamp)) CLICK-DELTA)
                                     (not= (:mode app-state) :quick-write))
                            (omt/switch-active-sheet component unit-tree (comp-dir e)))) }
     (when child-tree
       [:div.map-tuple-child (rum/with-key (node/node child-tree app-state) 2)])
     [ :div.map-tuple-clicker-left [:i.fa.fa-2x.fa-angle-left] ]
     [ :div.map-tuple-clicker-right [:i.fa.fa-2x.fa-angle-right] ]

     ]))

(rum/defcc map-tuple-component < trum/istatic lc/parser-type-mixin-context
  (rum/local { :unroll false :time-stamp 0} )
  [component unit-tree app-state]
  (let [local-state (trum/comp->local-state component)]
    (if (= (app-state :mode) :write)
      (render-write-mode component unit-tree app-state local-state)
      (render-read-mode component unit-tree app-state local-state))))

(defn- gen-toolbar []
 [[{:elem :btn
    :id "previous-page"
    :icon "far fa-arrow-left"
    :title "Previous page"
    :on-click #(omt/switch-active-sheet (:component %1) (:unit-tree %1) -1)
    :disabled #(ot/first-sheet? (:unit-tree %1)) }
   {:elem :btn
    :id "next-page"
    :icon "far fa-arrow-right"
    :title "Next page"
    :on-click #(omt/switch-active-sheet (:component %1) (:unit-tree %1) 1)
    :disabled #(ot/last-sheet? (:unit-tree %1)) }
   {:elem :text
    :id "pages"
    :value #(ot/sheets-to-str (:unit-tree %1)) }
   {:elem :btn
    :id "add-page"
    :icon "far fa-plus-circle"
    :title "Add page at the end"
    :on-click #(omt/new-sheet (:component %1) (:unit-tree %1))
    :hidden #(= (:mode %1) :read) }
   {:elem :btn
    :id "remove-page"
    :icon "far fa-minus-circle"
    :title "Remove current page"
    :on-click #(omt/remove-active-sheet (:component %1) (:unit-tree %1))
    :disabled #(<= ((ot/get-sheet-number (:unit-tree %1)) 1) 1)
    :hidden #(= (:mode %1) :read) }
   {:elem :btn
    :id "open-page"
    :icon "far fa-sign-in-alt"
    :title "Open current page"
    :on-click #(open-unit (:component %1) (:unit-tree %1))
    :disabled #(ot/no-sheets? (:unit-tree %1)) }
  ]])

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
  :orgpad/view-name           "Book View"
  :orgpad/view-icon           "far fa-book"

  :orgpad/propagate-props-from-children? true
  :orgpad/propagated-props-from-children { :orgpad.map-view/vertex-props
                                             [:orgpad/view-type :orgpad/view-name :orgpad/view-style
                                              :orgpad/unit-width :orgpad/unit-height
                                              :orgpad/unit-border-color :orgpad/unit-bg-color
                                              :orgpad/unit-border-width :orgpad/unit-corner-x
                                              :orgpad/unit-corner-y :orgpad/unit-border-style] }

  :orgpad/toolbar (gen-toolbar)
  :orgpad/uedit-toolbar (gen-toolbar)

  })
