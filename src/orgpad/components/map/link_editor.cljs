(ns orgpad.components.map.link-editor
  (:require-macros [orgpad.tools.colls :refer [>-]])
  (:require [rum.core :as rum]
            [orgpad.cycle.life :as lc]
            [orgpad.components.menu.circle.component :as mc]
            [orgpad.tools.js-events :as jev]
            [orgpad.tools.geom :as geom :refer [-- ++ *c screen->canvas canvas->screen]]
            [orgpad.tools.orgpad :as ot]
            [orgpad.tools.orgpad-manipulation :as omt]
            [orgpad.tools.styles :as styles]
            [orgpad.tools.dom :as dom]
            [orgpad.tools.math :refer [normalize-range]]
            [orgpad.components.graphics.primitives :as g]
            [orgpad.components.graphics.primitives-svg :as sg]
            [orgpad.components.menu.toolbar.component :as tbar]
            [orgpad.components.menu.color.picker :as cpicker]
            [orgpad.components.atomic.atom-editor :as aeditor]
            [goog.string :as gstring]
            [goog.string.format]
            [orgpad.components.map.utils :refer [mouse-pos set-mouse-pos! start-link selected-unit-prop
                                                 swap-link-direction]]
            [orgpad.components.input.slider :as slider]
            [orgpad.components.editors.styles :as stedit]))

(def ^:private edge-menu-conf
  {:always-open? false
   :init-state true
   :init-rotate -135
   :init-scale 0.5
   :main-spring-config #js [500 30]
   :fly-out-radius 50
   :base-angle 30
   :separation-angle 50
   :child-diam 35
   :child-init-scale 0.2
   :child-init-rotation -180
   :main-diam 40
   :offset 0.4
   :child-class "circle-menu-child"
   :final-child-pos-fn mc/final-child-delta-pos-rot
   :child-spring-config #js [400 28]})

(defn- close-link-menu
  [local-state]
  ;; (js/console.log "close link menu")
  (js/setTimeout
   #(swap! local-state merge {:link-menu-show :none}) 200))

(defn- remove-link
  [component unit local-state]
  (swap! local-state assoc :selected-link nil)
  (lc/transact! component [[:orgpad.units/map-view-link-remove (ot/uid unit)]]))

(defn- make-label
  [component {:keys [props unit path-info] :as unit-tree} view-name pid mid-pt ev]
  (lc/transact! component [[:orgpad.units/make-lnk-vtx-prop
                            {:pos mid-pt
                             :context-unit pid
                             :view-name view-name
                             :unit-tree unit-tree
                             :style (lc/query component :orgpad/style
                                              {:view-type :orgpad.map-view/vertex-props-style
                                               :style-name "default"} {:disable-cache? true})}]])
  (omt/open-unit component (assoc-in unit-tree [:view :orgpad/view-type] :orgpad/atomic-view)))

(defn edge-unit-editor
  [component {:keys [view] :as unit-tree} app-state local-state]
  (let [select-link (@local-state :selected-link)]
    (when (and select-link (= (@local-state :link-menu-show) :yes))
      (let [[old-unit old-prop _ _ _ mid-pt] select-link
            [unit prop] (selected-unit-prop unit-tree (ot/uid old-unit) (old-prop :db/id) (:orgpad/view-type old-prop))]
        (when (and prop unit)
          [:div {}
           (mc/circle-menu
            (merge edge-menu-conf {:center-x (mid-pt 0)
                                   :center-y (mid-pt 1)
                                   :onMouseDown jev/block-propagation
                                     ;; :onMouseUp jev/block-propagation
})
            [:i.far.fa-cogs.fa-lg {:title "Properties" :onMouseDown #(close-link-menu local-state)}]
            [:i.far.fa-file-edit.fa-lg
             {:title "Label"
              :onMouseUp (partial make-label component unit (:orgpad/view-name view) (ot/uid unit-tree) mid-pt)}]
            [:i.far.fa-exchange.fa-lg {:title "Flip direction" :onMouseDown (partial swap-link-direction component unit)}]
            [:i.far.fa-times.fa-lg {:title "Remove" :onMouseDown #(remove-link component unit local-state)}])])))))
