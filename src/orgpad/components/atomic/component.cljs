(ns ^{:doc "Atomic component"}
  orgpad.components.atomic.component
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.cycle.life :as lc]
            [orgpad.components.registry :as registry]
            [orgpad.components.atomic.atom-editor :as atom-editor]
            [orgpad.components.atomic.tags-editor :as tags-editor]
            [orgpad.components.atomic.desc-editor :as desc-editor]
            [orgpad.tools.rum :as trum]
            [orgpad.tools.orgpad :as ot]
            [orgpad.tools.js-events :as jev]))

(def ^:private mathjax-render-state (atom {}))

(defn- set-mathjax-render-state
  [unit-tree state & [component]]
  (if state
    (swap! mathjax-render-state assoc (ot/uid unit-tree) true)
    (swap! mathjax-render-state dissoc (ot/uid unit-tree)))
  (when component
    (let [[_ app-state] (trum/comp->args component)]
      (when (= (:mode app-state) :quick-write)
        (rum/request-render component)))))

(defn- update-mathjax
  [state]
  (let [dom-node (trum/ref state :dom-node)
        [unit-tree app-state] (trum/args state)
        component (:rum/react-component state)]
    (when (and js/MathJax
               (= (:mode app-state) :read))
      (let [hub (aget js/MathJax "Hub")
            queue (.bind (aget hub "Queue") hub)]
        (set-mathjax-render-state unit-tree true)
        (queue #js ["Typeset" hub dom-node]
               #js [set-mathjax-render-state unit-tree false component])))))

(defn- render-write-mode
  [{:keys [unit view]} app-state]
  [ :div { :className "atomic-view" }
    (rum/with-key ( desc-editor/desc-editor (unit :db/id) view (view :orgpad/desc) ) 0)
    (rum/with-key ( tags-editor/tags-editor (unit :db/id) view (view :orgpad/tags) ) 1)
    (rum/with-key ( atom-editor/atom-editor (unit :db/id) view (view :orgpad/atom) ) 2)
   ] )

(defn- render-quick-write-mode
  [{:keys [unit view]} app-state]
  [:div {:onMouseDown jev/stop-propagation
         :onMouseUp jev/stop-propagation
         :onClick jev/stop-propagation
         :onWheel jev/stop-propagation}
   (atom-editor/atom-editor (unit :db/id) view (view :orgpad/atom) :inline)])

(defn render-read-mode
  [{:keys [view]} app-state & [no-ref?]]
    [ :div (-> { :className "atomic-view" } (as-> x (if no-ref? x (assoc x :ref "dom-node"))))
      (when (and (view :orgpad/desc) (not= (view :orgpad/desc) ""))
        [ :div { :key 0 } (view :orgpad/desc)])
      (when (and (view :orgpad/tags) (not= (view :orgpad/tags) []))
        [ :div { :key 1} [ :div {} (html (into [] (map-indexed (fn [idx tag] (html [ :span { :key idx :className "react-tagsinput-tag" } tag ])) (view :orgpad/tags)))) ] ])
      (when (and (view :orgpad/atom) (not= (view :orgpad/atom) ""))
        [ :div  {:dangerouslySetInnerHTML
                 {:__html (view :orgpad/atom)} } ])
     ])

(rum/defc atomic-component < trum/istatic lc/parser-type-mixin-context (trum/gen-update-mixin update-mathjax)
  [unit-tree app-state]
  (if (= (:mode app-state) :write)
    (render-write-mode unit-tree app-state)
    (if (and (= (:mode app-state) :quick-write)
             (-> @mathjax-render-state (get (ot/uid unit-tree) false) not))
        (render-quick-write-mode unit-tree app-state)
        (render-read-mode unit-tree app-state))))

(registry/register-component-info
 :orgpad/atomic-view
 { :orgpad/default-view-info   { :orgpad/view-type :orgpad/atomic-view
                                 :orgpad/view-name "default" }
   :orgpad/class               atomic-component
   :orgpad/needs-children-info false
   :orgpad/view-name           "Sheet View"
   :orgpad/view-icon           "far fa-file-alt"
  })
