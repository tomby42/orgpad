(ns ^{:doc "Map unit component"}
  orgpad.components.map.unit
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [com.rpl.specter :as s :refer-macros [select transform]]
            [orgpad.cycle.life :as lc]
            [orgpad.components.registry :as registry]
            [orgpad.components.node :as node]
            [orgpad.components.map.unit-editor :as uedit]
            [orgpad.tools.css :as css]
            [orgpad.tools.colls :as colls]
            [orgpad.tools.rum :as trum]
            [orgpad.tools.geom :refer [++ -- *c normalize] :as geom]
            [orgpad.tools.js-events :as jev]
            [orgpad.tools.orgpad :refer [mapped-children mapped-links] :as ot]
            [orgpad.tools.orgpad-manipulation :as omt]
            [orgpad.tools.styles :as styles]
            [orgpad.tools.bezier :as bez]
            [orgpad.tools.math :as math]
            [orgpad.tools.geocache :as geocache]
            [orgpad.tools.func :as func]
            [orgpad.components.graphics.primitives :as g]
            [orgpad.components.graphics.primitives-svg :as sg]
            [orgpad.components.map.utils :refer [mouse-pos set-mouse-pos! start-change-link-shape parent-id
                                                 try-deselect-unit]]
            [orgpad.components.map.node :refer [map-unit]]))

(def mapped-children-mem
  (memoize mapped-children))

(def mapped-links-mem
  (memoize mapped-links))

(def map-unit-mem
  (func/memoize' map-unit {:key-fn #(-> % first ot/uid)
                           :eq-fns [identical? identical? identical? identical? identical? identical?]}))

(def ^:private link-eq-fns [identical? = identical? identical? identical? identical? identical?])

(defn- update-geocache-for-link-changes
  [component pid view-name uid start-pos end-pos mid-pt-rel refs cyclic? start-size]
  (let [global-cache (lc/get-global-cache component)
        bbox (geom/link-bbox start-pos end-pos mid-pt-rel)
        id1 (-> refs (nth 0) ot/uid-safe)
        id2 (-> refs (nth 1) ot/uid-safe)
        pos (bbox 0)
        size (geom/ensure-size (-- (bbox 1) (bbox 0)))
        [old-pos old-size] (aget global-cache uid "link-info" view-name)]
    (aset global-cache uid "link-info" view-name [pos size])
    (geocache/update-box! global-cache pid view-name uid
                          (if cyclic? (ot/left-top pos start-size) pos) size old-pos old-size
                          #js[id1 id2])))

(rum/defcc map-link < (trum/statical link-eq-fns) lc/parser-type-mixin-context
  [component {:keys [props unit] :as unit-tree} {:keys [start-pos end-pos cyclic? start-size]}
   app-state pcomponent view-name pid local-state]
  (try
    (let [prop (ot/get-props-view-child-styled props view-name pid
                                               :orgpad.map-view/link-props :orgpad.map-view/link-props-style
                                               :orgpad/map-view)
          mid-pt (geom/link-middle-point start-pos end-pos (prop :orgpad/link-mid-pt))
          style-svg {:css {:zIndex -1}
                     :svg (styles/gen-svg-link-canvas prop)}
          ctl-style (css/transform {:translate (-- (++ mid-pt [(-> prop :orgpad/link-width)
                                                               (-> prop :orgpad/link-width)])
                                                   [10 10])})
          ctl-pt (geom/link-middle-ctl-point start-pos end-pos mid-pt)
          start-pos' (when cyclic? (ot/left-top start-pos start-size))
          mid-pt' (when cyclic? (ot/left-top mid-pt start-size))]
      ;; (js/window.console.log "rendering " (unit :db/id))
      ;; ugly o'hacks
      ;; move it to component mount and component did update
      (update-geocache-for-link-changes pcomponent pid view-name (unit :db/id)
                                        start-pos end-pos (prop :orgpad/link-mid-pt)
                                        (unit :orgpad/refs) cyclic? start-size)
      (html
       [:div {}
        (if cyclic?
          (sg/arc (geom/link-arc-center start-pos' mid-pt')
                  (geom/link-arc-radius start-pos' mid-pt')
                  0 math/pi2 style-svg)
          (sg/quadratic-curve start-pos end-pos ctl-pt style-svg))
        (when (not= (prop :orgpad/link-type) :undirected)
          (if cyclic?
            (sg/make-arrow-arc start-pos' mid-pt' prop style-svg)
            (sg/make-arrow-quad start-pos end-pos ctl-pt prop style-svg)))
        (when (and (= (prop :orgpad/link-type) :bidirected) (not cyclic?))
          (sg/make-arrow-quad end-pos start-pos ctl-pt prop style-svg))]))
    (catch :default e
      (js/console.log "link render error" unit-tree start-pos end-pos cyclic? view-name pid  e) ;; TODO - show error
      nil)))

(def map-link-mem
  (func/memoize' map-link {:key-fn #(-> % first ot/uid)
                           :eq-fns link-eq-fns}))

(defn render-mapped-children-units
  [component {:keys [unit view props] :as unit-tree} app-state local-state]
  (let [style (merge (css/transform (:orgpad/transform view))
                     {})
        view-name (:orgpad/view-name view)
        pid (:db/id unit) ;;(parent-id view)
        m-units (mapped-children-mem unit view-name)
        m-links (mapped-links-mem unit view-name pid m-units)]
    ;; (js/console.log m-units m-links)
    (aset component "parent-view" view)
    (html
     [:div
      (conj
       (colls/minto [:div {:className "map-view-canvas" :style style}]
                    (map #(map-link-mem (% 0) (% 1) app-state component view-name pid local-state) m-links)
                    (map #(map-unit-mem % app-state component view-name pid local-state) m-units))
       (uedit/unit-editor unit-tree app-state local-state))
      (when (= (app-state :mode) :write)
        (uedit/unit-editor-static unit-tree app-state local-state))])))
