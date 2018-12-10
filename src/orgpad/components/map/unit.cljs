(ns ^{:doc "Map unit component"}
  orgpad.components.map.unit
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [com.rpl.specter :as s :refer-macros [select transform]]
            [orgpad.cycle.life :as lc]
            [orgpad.components.registry :as registry]
            [orgpad.components.node :as node]
            [orgpad.components.map.unit-editor :as uedit]
            [orgpad.components.sidebar.sidebar :as sidebar]
            [orgpad.components.atomic.component :as catomic]
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
            [orgpad.components.map.utils :refer [mouse-pos set-mouse-pos! start-change-link-shape parent-id]]))

(defn- select-unit
  [unit-tree prop pcomponent local-state component]
  (swap! local-state merge {:selected-unit [unit-tree prop (aget pcomponent "parent-view") component]}))

(def mapped-children-mem
  (memoize mapped-children))

(def mapped-links-mem
  (memoize mapped-links))

(defn- open-unit
  [component unit-tree local-state]
  (when (= (@local-state :local-mode) :try-unit-move)
    (omt/open-unit component unit-tree)))

(def ^:private finc (fnil inc 0))

(def ^:private dbl-click-timeout 250)

;; (defn- run-dbl-click-check
;;   [local-state]
;;   (js/setTimeout (fn []
;;                    (when (> (:pre-quick-edit @local-state) 1)
;;                      (uedit/enable-quick-edit local-state))
;;                    (swap! local-state assoc :pre-quick-edit 0))
;;                  dbl-click-timeout))

(defn- try-move-unit
  [component unit-tree app-state prop pcomponent local-state ev]
  (jev/block-propagation ev)
  (let [old-node (:selected-node @local-state)
        new-node (-> component rum/state deref (trum/ref-node "unit-node"))
        parent-view (aget pcomponent "parent-view")
        ;; pre-quick-edit (:pre-quick-edit @local-state)
        ]
    (when old-node
      (aset old-node "style" "z-index" "0"))
    (when new-node
      (aset new-node "style" "z-index" "1"))
    ;; (when new-node
    ;;   (aset new-node "style" "z-index" (if (:quick-edit @local-state) "2" "1")))
    ;; (when (and (= (:mode app-state) :write)
    ;;            (or (=  pre-quick-edit 0)
    ;;                (not pre-quick-edit)))
    ;;   (run-dbl-click-check local-state))
    (swap! local-state merge {:local-mode :try-unit-move
                              :selected-unit [unit-tree prop parent-view component]
                              :selected-node new-node
                              :quick-edit false
                              ;; :pre-quick-edit (finc pre-quick-edit)
                              :start-mouse-x (.-clientX (jev/touch-pos ev))
                              :start-mouse-y (.-clientY (jev/touch-pos ev))
                              :mouse-x (.-clientX (jev/touch-pos ev))
                              :mouse-y (.-clientY (jev/touch-pos ev))})
    (set-mouse-pos! (jev/touch-pos ev))
    (comment (lc/transact! component [[:orgpad.units/select {:pid (parent-id parent-view)
                                                             :toggle? (.-ctrlKey ev)
                                                             :uid (ot/uid unit-tree)}]]))
    ))

(defn- sheet-indicator
  [active? id]
  [:i {:className (str (if active? "fa" "far") " fa-circle") :id id}])

(defn- insert-sheet-indicators
  [unit-tree color]
  (let [[active-sheet total-sheets] (ot/get-sheet-number unit-tree)]
    (when (> total-sheets 1)
      [:div.tuple-page-indicator {:style {:color color}}
       (map
        #(sheet-indicator (= % (dec active-sheet)) (str "circle-" %))
        (range total-sheets))])))

(rum/defcc map-unit < trum/istatic lc/parser-type-mixin-context
  [component {:keys [props unit] :as unit-tree} app-state pcomponent view-name pid local-state]
  (try
    (let [prop (ot/get-props-view-child-styled props view-name pid
                                               :orgpad.map-view/vertex-props
                                               :orgpad.map-view/vertex-props-style
                                               :orgpad/map-view)
          pos (-- (prop :orgpad/unit-position)
                  [(/ (prop :orgpad/unit-width) 2) (/ (prop :orgpad/unit-height) 2)])
          selections (get-in app-state [:selections pid])
          selected? (= (:db/id unit) (first selections))
          ;; selected? (= (unit :db/id) (-> @local-state :selected-unit first ot/uid))
          border-color (-> prop :orgpad/unit-border-color css/format-color)
          style-pos (css/transform {:translate pos})
          style (merge (styles/prop->css prop)
                       (when selected?
                         ;; {:zIndex (if (:quick-edit @local-state) 2 1)}
                         {:zIndex 1}
                         ))]
      ;;(js/window.console.log "rendering " (unit :db/id) (and selected? (:quick-edit @local-state)))
      (when selected?
        (select-unit unit-tree prop pcomponent local-state component))
      (html
       [:div {:id (str "unit-" pid "-" (:db/id unit))
              :className "map-view-child-container"
              :style style-pos
              :ref "unit-node"}
        (when (contains? selections (:db/id unit))
          [:div {:className "map-view-child"
                 :style (-> style
                            (update :borderWidth + 2)
                            (assoc :borderColor "rgba(120,50,50,0.5)"))}])
        [:div
         (if (= (app-state :mode) :write)
           {:style style :className "map-view-child" :key (unit :db/id)
            :onMouseDown #(try-move-unit component unit-tree app-state prop pcomponent local-state %)
            :onTouchStart #(try-move-unit component unit-tree app-state prop pcomponent local-state %)
            ;; :onMouseUp (jev/make-block-propagation #(swap! local-state merge { :local-mode :none }))
            :onMouseUp (partial uedit/try-deselect-unit component pid (:db/id unit) local-state)
            ;; :onDoubleClick (jev/make-block-propagation #(uedit/enable-quick-edit local-state))
            :onWheel jev/stop-propagation
            }
           {:style style :className "map-view-child" :key (unit :db/id)
            :onMouseDown #(try-move-unit component unit-tree app-state prop pcomponent local-state %)
            :onTouchStart #(try-move-unit component unit-tree app-state prop pcomponent local-state %)
            :onWheel jev/stop-propagation
            })
         (node/node unit-tree
                    (assoc app-state
                           :mode
                           ;; (if (and selected? (:quick-edit @local-state))
                           ;;   :quick-write
                           ;;   :read)
                           :read
                           ))
         (if (= (app-state :mode) :write)
           (when-not (and selected? (:quick-edit @local-state))
             [:div.map-view-child.hat
              {:style {:top 0
                       :width (prop :orgpad/unit-width)
                       :height (prop :orgpad/unit-height)}
               :onMouseDown #(try-move-unit component unit-tree app-state prop pcomponent local-state %)}])
           [:div.map-view-child.leader-control
            {:style {:left (/ (prop :orgpad/unit-corner-x) 2)}
             :onMouseDown #(try-move-unit component unit-tree app-state prop pcomponent local-state %)
             :onTouchStart #(try-move-unit component unit-tree app-state prop pcomponent local-state %)
             :onMouseUp #(open-unit pcomponent unit-tree local-state)}
            [:i.far.fa-sign-in-alt]])
         (when (= (ot/view-type unit-tree) :orgpad/map-tuple-view)
           (insert-sheet-indicators unit-tree border-color))
         ;;(when (contains? selections (:db/id unit))
         ;;  [:span.fa.fa-check-circle.fa-lg.select-check {:style {:right (+ (/ (prop :orgpad/unit-corner-y) 2) 8) }}])
]]))
    (catch :default e
      (js/console.log "Unit render error" e)
      nil)))

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

(defn- mk-lnk-vtx-prop
  [component {:keys [props unit path-info] :as unit-tree} view-name pid mid-pt]
  (let [prop (ot/get-props-view-child props view-name pid
                                      :orgpad.map-view/vertex-props)]
    (when (nil? prop)
      (js/setTimeout
       (fn []
         (lc/transact! component [[:orgpad.units/make-lnk-vtx-prop
                                   {:pos mid-pt
                                    :context-unit pid
                                    :view-name view-name
                                    :unit-tree unit-tree
                                    :style (lc/query component :orgpad/style
                                                     {:view-type :orgpad.map-view/vertex-props-style
                                                      :style-name "default"} true)}]])) 0))))

(rum/defcc map-link < (trum/statical link-eq-fns) lc/parser-type-mixin-context
  [component {:keys [props unit] :as unit-tree} {:keys [start-pos end-pos cyclic? start-size]}
   app-state pcomponent view-name pid local-state]
  (try
    (let [prop (ot/get-props-view-child-styled props view-name pid
                                               :orgpad.map-view/link-props :orgpad.map-view/link-props-style
                                               :orgpad/map-view)
          mid-pt (geom/link-middle-point start-pos end-pos (prop :orgpad/link-mid-pt))
          ;; style {:css {:zIndex -1}
          ;;        :canvas (styles/gen-link-canvas prop)}
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
      (when (ot/get-props-no-ctx (:orgpad/props-refs unit) view-name :orgpad/atomic-view :orgpad/unit-view)
        (mk-lnk-vtx-prop component unit-tree view-name pid mid-pt))
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
        view-name (view :orgpad/view-name)
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

(defn- do-move-to-unit
  [component params ev]
  (.stopPropagation ev)
  (lc/transact! component [[:orgpad.units/map-move-to-unit params]]))

(defn- render-selected-unit
  [component app-state parent-view [uid vprop tprops]]
  [:div.map-selected-unit {:key uid
                           :onMouseDown jev/stop-propagation
                           :onClick (partial do-move-to-unit component {:uid uid
                                                                        :vprop (get-in vprop [0 1])
                                                                        :parent-view parent-view})}
   (map (fn [prop]
          [:div {:key (-> prop second :db/id)}
           (catomic/render-read-mode {:view (prop 1)} app-state true)]) tprops)])

(defn- render-selection-
  [component {:keys [view unit props]} app-state local-state]
  (let [selection (get-in app-state [:selections (:db/id unit)])
        vertex-props (lc/query component :orgpad/selection-vertex-props
                               {:id (:db/id unit) :view view :selection selection} true)
        text-props (lc/query component :orgpad/selection-text-props
                             {:id (:db/id unit) :view view :selection selection} true)]
    (map (comp (partial render-selected-unit component app-state view)
               (juxt identity vertex-props text-props))
         selection)))

(def ^:private selection-eq-fns [identical? = identical? identical?])
(def ^:private render-selection
  (func/memoize' render-selection- {:key-fn #(-> % second ot/uid)
                                    :eq-fns selection-eq-fns}))

(defn render-selected-children-units
  [component unit-tree app-state local-state]
  (sidebar/sidebar-component :left
                             #(render-selection- component unit-tree app-state local-state)))
