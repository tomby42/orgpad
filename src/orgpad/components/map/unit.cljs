(ns ^{:doc "Map unit component"}
  orgpad.components.map.unit
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.cycle.life :as lc]
            [orgpad.components.registry :as registry]
            [orgpad.components.node :as node]
            [orgpad.components.map.unit-editor :as uedit]
            [orgpad.tools.css :as css]
            [orgpad.tools.colls :as colls]
            [orgpad.tools.rum :as trum]
            [orgpad.tools.geom :as geom]
            [orgpad.tools.js-events :as jev]
            [orgpad.tools.orgpad :as ot]
            [orgpad.tools.bezier :as bez]
            [orgpad.tools.math :as math]
            [orgpad.tools.geocache :as geocache]
            [orgpad.components.graphics.primitives :as g]))

(defn- parent-id
  [view]
  (-> view :orgpad/refs first :db/id))

(defn- select-unit
  [unit-tree prop pcomponent local-state]
  (swap! local-state merge { :selected-unit [unit-tree prop (aget pcomponent "parent-view")] }))

(defn- mapped?
  [{:keys [orgpad/refs db/id]} view-name prop-name]
  (let [pred (partial ot/props-pred-view-child id view-name prop-name)]
    (filter (fn [u] (->> u :props (some pred))) refs)))

(defn- mapped-children
  [unit-tree view-name]
  (mapped? unit-tree view-name :orgpad.map-view/vertex-props))

(def mapped-children-mem
  (memoize mapped-children))

(defn- get-pos
  [u view-name pid]
  (-> u :props (ot/get-props-view-child view-name pid :orgpad.map-view/vertex-props) :orgpad/unit-position))

(defn- mapped-links
  [unit-tree view-name pid m-units]
  (let [links (mapped? unit-tree view-name :orgpad.map-view/link-props)
        mus   (into {} (map (fn [u] [(ot/uid u) u])) m-units)]
    (map (fn [l]
           (let [refs (-> l :unit :orgpad/refs)
                 id1 (-> refs (nth 0) ot/uid)
                 id2 (-> refs (nth 1) ot/uid)]
             [l { :start-pos (get-pos (mus id1) view-name pid)
                  :end-pos (get-pos (mus id2) view-name pid)
                  :cyclic? (= id1 id2) }]))
         links)))

(def mapped-links-mem
  (memoize mapped-links))

(defn- open-unit
  [component unit-tree local-state]
  (when (= (@local-state :local-mode) :try-unit-move)
    (uedit/open-unit component unit-tree)))

(defn- try-move-unit
  [component unit-tree prop pcomponent local-state ev]
  (.stopPropagation ev)
  (let [old-node (:selected-node @local-state)
        new-node (-> component rum/state deref (trum/ref-node "unit-node"))
        parent-view (aget pcomponent "parent-view")]
    (when old-node
      (aset old-node "style" "z-index" "0"))
    (when new-node
      (aset new-node "style" "z-index" "1"))
    (swap! local-state merge { :local-mode :try-unit-move
                               :selected-unit [unit-tree prop parent-view]
                               :selected-node new-node
                               :show-local-menu false
                               :mouse-x (.-clientX (jev/touch-pos ev))
                               :mouse-y (.-clientY (jev/touch-pos ev)) })
    (lc/transact! component [[ :orgpad.units/select {:pid (parent-id parent-view)
                                                     :uid (ot/uid unit-tree)} ]])))

(rum/defcc map-unit < trum/istatic lc/parser-type-mixin-context
  [component {:keys [props unit] :as unit-tree} app-state pcomponent view-name pid local-state]
  (let [prop (ot/get-props-view-child props view-name pid :orgpad.map-view/vertex-props)
        pos (prop :orgpad/unit-position)
        selected? (= (unit :db/id) (get-in app-state [:selections pid 0]))
        ;; selected? (= (unit :db/id) (-> @local-state :selected-unit first ot/uid))
        style (merge { :width (prop :orgpad/unit-width)
                       :height (prop :orgpad/unit-height)
                       :borderWidth (prop :orgpad/unit-border-width)
                       :borderStyle (prop :orgpad/unit-border-style)
                       :borderColor (prop :orgpad/unit-border-color)
                       :borderRadius (str (prop :orgpad/unit-corner-x) "px "
                                          (prop :orgpad/unit-corner-y) "px")
                       :backgroundColor (prop :orgpad/unit-bg-color) }
                     (css/transform { :translate pos })) ]
    ;; (js/window.console.log "rendering " (unit :db/id))
    (when selected?
      (select-unit unit-tree prop pcomponent local-state))
    (html
     [ :div
      (if (= (app-state :mode) :write)
        { :style style :className "map-view-child" :key (unit :db/id)
          :onMouseDown #(try-move-unit component unit-tree prop pcomponent local-state %)
          :onTouchStart #(try-move-unit component unit-tree prop pcomponent local-state %)
          :onMouseUp (jev/make-block-propagation #(swap! local-state merge { :local-mode :none }))
          :ref "unit-node"
         }
        { :style style :className "map-view-child" :key (unit :db/id)
          :onMouseDown #(try-move-unit component unit-tree prop pcomponent local-state %)
          :onTouchStart #(try-move-unit component unit-tree prop pcomponent local-state %)
          :ref "unit-node"
         })
       (node/node unit-tree (assoc app-state :mode :read))
       (if (= (app-state :mode) :write)
         [ :div.map-view-child
           { :style { :top 0
                      :width (prop :orgpad/unit-width)
                      :height (prop :orgpad/unit-height) }
             :onMouseDown #(try-move-unit component unit-tree prop pcomponent local-state %) } ]
         [ :div.map-view-child.leader-control
           { :style { :top -10 :left -10 }
             :onMouseDown #(try-move-unit component unit-tree prop pcomponent local-state %)
             :onTouchStart #(try-move-unit component unit-tree prop pcomponent local-state %)
             :onMouseUp #(open-unit pcomponent unit-tree local-state) } ]
         )
      ])))

(def map-unit-mem
  (colls/memoize' map-unit {:key-fn #(-> % first ot/uid)
                            :eq-fns [identical? identical? identical? identical? identical? identical?]}))

(defn- start-change-link-shape
  [unit-tree prop component start-pos end-pos mid-pt local-state ev]
  (.stopPropagation ev)
  (let [parent-view (aget component "parent-view")]
    (swap! local-state merge { :local-mode :link-shape
                               :selected-link [unit-tree prop parent-view start-pos end-pos mid-pt]
                               :link-menu-show :maybe
                               :selected-unit nil
                               :mouse-x (if (.-clientX ev) (.-clientX ev) (aget ev "touches" 0 "clientX"))
                               :mouse-y (if (.-clientY ev) (.-clientY ev) (aget ev "touches" 0 "clientY")) })
    (lc/transact! component [[ :orgpad.units/deselect-all {:pid (parent-id parent-view)} ]])))

(defn- make-arrow-quad
  [start-pos end-pos ctl-pt prop]
  (let [p1 (bez/get-point-on-quadratic-bezier start-pos ctl-pt end-pos 0.85)
        dir (-> p1 (geom/-- (bez/get-point-on-quadratic-bezier start-pos ctl-pt end-pos 0.84)) geom/normalize)
        ptmp (geom/++ p1 (geom/*c dir -10))
        n (-> dir geom/normal)
        p2 (geom/++ ptmp (geom/*c n 10))
        p3 (geom/++ ptmp (geom/*c (geom/-- n) 10))
        style { :css { :zIndex -1 }
                :canvas { :strokeStyle (prop :orgpad/link-color)
                          :lineWidth (prop :orgpad/link-width)
                          :lineCap "round" } }]
    (g/poly-line [p2 p1 p3] style)))

(defn- make-arrow-arc
  [s e prop]
  (let [dir (geom/normalize (geom/-- s e))
        n (geom/normal dir)
        s' (geom/++ (geom/*c n -10) s)
        p1 (geom/++ (geom/*c dir 10) s')
        p2 (geom/++ (geom/*c dir -10) s')
        style { :css { :zIndex -1 }
                :canvas { :strokeStyle (prop :orgpad/link-color)
                          :lineWidth (prop :orgpad/link-width)
                          :lineCap "round" } }]
    (g/poly-line [p1 s p2] style)))

(def ^:private link-eq-fns [identical? = identical? identical? identical? identical? identical?])

(defn- update-geocahce-for-link-changes
  [component pid view-name uid start-pos end-pos mid-pt-rel refs]
  (let [global-cache (lc/get-global-cache component)
        bbox (geom/link-bbox start-pos end-pos mid-pt-rel)
        id1 (-> refs (nth 0) ot/uid)
        id2 (-> refs (nth 1) ot/uid)
        pos (bbox 0)
        size (geom/-- (bbox 1) (bbox 0))
        [old-pos old-size] (aget global-cache uid "link-info" view-name)]
    (aset global-cache uid "link-info" view-name [pos size])
    (geocache/update-box! global-cache pid view-name uid
                          pos size old-pos old-size
                          #js[id1 id2])))

(rum/defcc map-link < (trum/statical link-eq-fns) lc/parser-type-mixin-context
  [component {:keys [props unit] :as unit-tree} {:keys [start-pos end-pos cyclic?]} app-state pcomponent view-name pid local-state]
  (let [prop (ot/get-props-view-child props view-name pid :orgpad.map-view/link-props)
        mid-pt (geom/link-middle-point start-pos end-pos (prop :orgpad/link-mid-pt))
        style { :css { :zIndex -1 }
                :canvas { :strokeStyle (prop :orgpad/link-color)
                          :lineWidth (prop :orgpad/link-width)
                          :lineCap "round"
                          :lineDash (prop :orgpad/link-dash) } }
        ctl-style (css/transform {:translate (geom/-- mid-pt [10 10])})
        ctl-pt (geom/link-middle-ctl-point start-pos end-pos mid-pt)]
    ;; (js/window.console.log "rendering " (unit :db/id))
    ;; ugly o'hack
    (update-geocahce-for-link-changes pcomponent pid view-name (unit :db/id)
                                      start-pos end-pos (prop :orgpad/link-mid-pt)
                                      (unit :orgpad/refs))
    (html
     [ :div {}
       (if cyclic?
         (g/arc (geom/link-arc-center start-pos mid-pt)
                (geom/link-arc-radius start-pos mid-pt) 0 math/pi2 style)
         (g/quadratic-curve start-pos end-pos ctl-pt style))
       (if cyclic?
         (make-arrow-arc start-pos mid-pt prop)
         (make-arrow-quad start-pos end-pos ctl-pt prop))
       (when (= (app-state :mode) :write)
         [ :div { :className "map-view-child link-control" :style ctl-style
                  :onMouseDown #(start-change-link-shape unit-tree prop pcomponent start-pos end-pos mid-pt local-state %)
                  :onTouchStart #(start-change-link-shape unit-tree prop pcomponent start-pos end-pos mid-pt local-state %) } ]) ])))

(def map-link-mem
  (colls/memoize' map-link {:key-fn #(-> % first ot/uid)
                            :eq-fns link-eq-fns}))

(defn render-mapped-children-units
  [component {:keys [unit view props] :as unit-tree} app-state local-state]
  (let [style (merge (css/transform (:orgpad/transform view))
                     {})
        view-name (view :orgpad/view-name)
        pid (parent-id view)
        m-units (mapped-children-mem unit view-name)
        m-links (mapped-links-mem unit view-name pid m-units)]
    (aset component "parent-view" view)
    (html
     (conj
      (colls/minto [ :div { :className "map-view-canvas" :style style } ]
                   (map #(map-link-mem (% 0) (% 1) app-state component view-name pid local-state) m-links)
                   (map #(map-unit-mem % app-state component view-name pid local-state) m-units))
      (when (= (app-state :mode) :write)
        (uedit/unit-editor unit-tree app-state local-state)))) ))
