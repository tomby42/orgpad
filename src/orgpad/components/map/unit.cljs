(ns ^{:doc "Map unit component"}
  orgpad.components.map.unit
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.cycle.life :as lc]
            [orgpad.components.registry :as registry]
            [orgpad.components.menu.circle :as mc]
            [orgpad.components.node :as node]
            [orgpad.components.map.unit-editor :as uedit]
            [orgpad.tools.css :as css]
            [orgpad.tools.colls :as colls]
            [orgpad.tools.rum :as trum]
            [orgpad.tools.geom :as geom]
            [orgpad.components.graphics.primitives :as g]))

;; TODO configure ??
(def ^:private default-canvas-size 99999)

(defn- select-unit
  [unit-tree prop parent-view local-state]
  (swap! local-state merge { :selected-unit [unit-tree prop parent-view] }))

(defn- props-pred
  [view-name view-type v]
  (and v
       (= (v :orgpad/view-type) view-type)
       (= (v :orgpad/type) :orgpad/unit-view-child)
       (= (v :orgpad/view-name) view-name)))

(defn- get-props
  [props {:keys [orgpad/view-name]} prop-name]
  (->> props
       (drop-while #(not (props-pred view-name prop-name %)))
       first))

(defn- mapped?
  [{:keys [orgpad/refs]} {:keys [orgpad/view-name]} prop-name]
  (let [pred (partial props-pred view-name prop-name)]
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
        mus   (into {} (map (fn [u] [(-> u :unit :db/id) u])) m-units)]
    (map (fn [l] (merge l { :start-pos (get-pos (mus (-> l :unit :orgpad/refs (nth 0) :unit :db/id)) view)
                            :end-pos (get-pos (mus (-> l :unit :orgpad/refs (nth 1) :unit :db/id)) view) }))
         links)))

(rum/defcc map-unit < rum/static lc/parser-type-mixin-context
  [component {:keys [props unit] :as unit-tree} app-state parent-view local-state]
  (let [prop (get-props props parent-view :orgpad.map-view/vertex-props)
        pos (prop :orgpad/unit-position)
        style (merge { :width (prop :orgpad/unit-width)
                       :height (prop :orgpad/unit-height)
                       :border (str (prop :orgpad/unit-border-width) "px "
                                    (prop :orgpad/unit-border-style) " "
                                    (prop :orgpad/unit-border-color))
                       :borderRadius (str (prop :orgpad/unit-corner-x) "px "
                                          (prop :orgpad/unit-corner-y) "px")
                       :backgroundColor (prop :orgpad/unit-bg-color) }
                     (css/transform { :translate pos })) ]
    (when (= (unit :db/id) (-> local-state deref :selected-unit first :unit :db/id))
      (select-unit unit-tree prop parent-view local-state))
    (html
     [ :div { :style style :className "map-view-child" :key (unit :db/id)
              :onMouseDown (if (= (app-state :mode) :write)
                             #(do
                                (select-unit unit-tree prop parent-view local-state)
                                (.stopPropagation %))
                             #(do
                                (swap! local-state merge { :local-mode :unit-move
                                                           :selected-unit [unit-tree prop parent-view]
                                                           :mouse-x (.-clientX %)
                                                           :mouse-y (.-clientY %) })
                                (.stopPropagation %))) }
       (node/node unit-tree (assoc app-state :mode :read))])))

(rum/defcc map-link < rum/static lc/parser-type-mixin-context
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
       (when (= (app-state :mode) :write)
         [ :div { :className "map-view-child link-control" :style ctl-style
                  :onMouseDown #(do
                                  (swap! local-state merge { :local-mode :link-shape
                                                             :selected-link [unit-tree prop parent-view start-pos end-pos mid-pt]
                                                             :link-menu-show :maybe
                                                             :selected-unit nil
                                                             :mouse-x (.-clientX %)
                                                             :mouse-y (.-clientY %) })
                                  (.stopPropagation %)) } ]) ])))

(defn render-mapped-children-units
  [component {:keys [unit view props] :as unit-tree} app-state local-state]
  (let [style (merge (css/transform (:orgpad/transform view))
                     { :width default-canvas-size
                       :height default-canvas-size })
        m-units (mapped-children unit view)
        m-links (mapped-links unit view m-units)]
    (html
     (conj
      (colls/minto [ :div { :className "map-view-canvas" :style style } ]
                   (map #(map-link % app-state view local-state) m-links)
                   (map #(map-unit % app-state view local-state) m-units))
      (when (= (app-state :mode) :write)
        (uedit/unit-editor unit-tree app-state local-state)))) ))
