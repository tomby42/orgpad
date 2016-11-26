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
            [orgpad.components.graphics.primitives :as g]))

(defn- select-unit
  [unit-tree prop parent-view local-state]
  (swap! local-state merge { :selected-unit [unit-tree prop parent-view] }))

(defn- props-pred
  [ctx-unit view-name view-type v]
  (and v
       (= (v :orgpad/context-unit) ctx-unit)
       (= (v :orgpad/view-type) view-type)
       (= (v :orgpad/type) :orgpad/unit-view-child)
       (= (v :orgpad/view-name) view-name)))

(defn- get-props
  [props {:keys [orgpad/view-name orgpad/refs]} prop-name]
  (let [pid (-> refs first :db/id)]
    (->> props
         (drop-while #(not (props-pred pid view-name prop-name %)))
         first)))

(defn- mapped?
  [{:keys [orgpad/refs db/id]} {:keys [orgpad/view-name]} prop-name]
  (let [pred (partial props-pred id view-name prop-name)]
    (filter (fn [u] (->> u :props (some pred))) refs)))

(defn- mapped-children
  [unit-tree view]
  (mapped? unit-tree view :orgpad.map-view/vertex-props))

(defn- get-pos
  [u parent-view]
  (-> u :props (get-props parent-view :orgpad.map-view/vertex-props) :orgpad/unit-position))

(defn- mapped-links
  [unit-tree view m-units]
  (let [links (mapped? unit-tree view :orgpad.map-view/link-props)
        mus   (into {} (map (fn [u] [(ot/uid u) u])) m-units)]
    (map (fn [l] (merge l { :start-pos (get-pos (mus (-> l :unit :orgpad/refs (nth 0) ot/uid)) view)
                            :end-pos (get-pos (mus (-> l :unit :orgpad/refs (nth 1) ot/uid)) view) }))
         links)))

(defn- open-unit
  [component unit-tree local-state]
  (when (= (@local-state :local-mode) :try-unit-move)
    (uedit/open-unit component unit-tree)))

(defn- try-move-unit
  [unit-tree prop parent-view local-state ev]
  (swap! local-state merge { :local-mode :try-unit-move
                             :selected-unit [unit-tree prop parent-view]
                             :mouse-x (.-clientX (jev/touch-pos ev))
                             :mouse-y (.-clientY (jev/touch-pos ev)) })
  (.stopPropagation ev))

(rum/defcc map-unit < trum/istatic lc/parser-type-mixin-context
  [component {:keys [props unit] :as unit-tree} app-state parent-view local-state]
  (let [prop (get-props props parent-view :orgpad.map-view/vertex-props)
        pos (prop :orgpad/unit-position)
        style (merge { :width (prop :orgpad/unit-width)
                       :height (prop :orgpad/unit-height)
                       :borderWidth (prop :orgpad/unit-border-width)
                       :borderStyle (prop :orgpad/unit-border-style)
                       :borderColor (prop :orgpad/unit-border-color)
                       :borderRadius (str (prop :orgpad/unit-corner-x) "px "
                                          (prop :orgpad/unit-corner-y) "px")
                       :backgroundColor (prop :orgpad/unit-bg-color) }
                     (css/transform { :translate pos })) ]
    (when (= (unit :db/id) (-> local-state deref :selected-unit first ot/uid))
      (select-unit unit-tree prop parent-view local-state))
    (html
     [ :div
      (if (= (app-state :mode) :write)
        { :style style :className "map-view-child" :key (unit :db/id)
          :onMouseDown #(try-move-unit unit-tree prop parent-view local-state %)
         }
        { :style style :className "map-view-child" :key (unit :db/id)
          :onMouseDown #(try-move-unit unit-tree prop parent-view local-state %)
          :onTouchStart #(try-move-unit unit-tree prop parent-view local-state %)
         })
       (node/node unit-tree (assoc app-state :mode :read))
       (if (= (app-state :mode) :write)
         [ :div.map-view-child
           { :style { :top 0
                      :width (prop :orgpad/unit-width)
                      :height (prop :orgpad/unit-height) }
             :onMouseDown #(try-move-unit unit-tree prop parent-view local-state %) } ]
         [ :div.map-view-child.link-control
           { :style { :top -20 :left -20 }
             :onMouseDown #(try-move-unit unit-tree prop parent-view local-state %)
             :onTouchStart #(try-move-unit unit-tree prop parent-view local-state %)
             :onMouseUp #(open-unit component unit-tree local-state) } ]
         )
      ])))

(defn- start-change-link-shape
  [unit-tree prop parent-view start-pos end-pos mid-pt local-state ev]
  (swap! local-state merge { :local-mode :link-shape
                             :selected-link [unit-tree prop parent-view start-pos end-pos mid-pt]
                             :link-menu-show :maybe
                             :selected-unit nil
                             :mouse-x (if (.-clientX ev) (.-clientX ev) (aget ev "touches" 0 "clientX"))
                             :mouse-y (if (.-clientY ev) (.-clientY ev) (aget ev "touches" 0 "clientY")) })
  (.stopPropagation ev))

(defn- make-arrow
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

(rum/defcc map-link < trum/istatic lc/parser-type-mixin-context
  [component {:keys [props unit start-pos end-pos] :as unit-tree} app-state parent-view local-state]
  (let [prop (get-props props parent-view :orgpad.map-view/link-props)
        mid-pt (geom/++ (geom/*c (geom/++ start-pos end-pos) 0.5) (prop :orgpad/link-mid-pt))
        style { :css { :zIndex -1 }
                :canvas { :strokeStyle (prop :orgpad/link-color)
                          :lineWidth (prop :orgpad/link-width)
                          :lineCap "round"
                          :lineDash (prop :orgpad/link-dash) } }
        ctl-style (css/transform {:translate (geom/-- mid-pt [10 10])})
        ctl-pt (geom/*c (geom/-- mid-pt (geom/*c start-pos 0.25) (geom/*c end-pos 0.25)) 2)]
    (html
     [ :div {}
       (g/quadratic-curve start-pos end-pos ctl-pt style)
       (make-arrow start-pos end-pos ctl-pt prop)
       (when (= (app-state :mode) :write)
         [ :div { :className "map-view-child link-control" :style ctl-style
                  :onMouseDown #(start-change-link-shape unit-tree prop parent-view start-pos end-pos mid-pt local-state %)
                  :onTouchStart #(start-change-link-shape unit-tree prop parent-view start-pos end-pos mid-pt local-state %) } ]) ])))

(defn render-mapped-children-units
  [component {:keys [unit view props] :as unit-tree} app-state local-state]
  (let [style (merge (css/transform (:orgpad/transform view))
                     {})
        m-units (mapped-children unit view)
        m-links (mapped-links unit view m-units)]
    (html
     (conj
      (colls/minto [ :div { :className "map-view-canvas" :style style } ]
                   (map #(map-link % app-state view local-state) m-links)
                   (map #(map-unit % app-state view local-state) m-units))
      (when (= (app-state :mode) :write)
        (uedit/unit-editor unit-tree app-state local-state)))) ))
