(ns orgpad.components.panel.index.component
  (:require [rum.core :as rum]
            treebeard
            [sablono.core :as html :refer-macros [html]]
            [orgpad.cycle.life :as lc]
            [orgpad.tools.orgpad :as ot]
            [orgpad.components.sidebar.sidebar :as sidebar]
            [orgpad.tools.js-events :as jev]
            [orgpad.components.registry :as registry]
            [orgpad.components.panel.index.style :as style]
            [orgpad.tools.rum :as trum]
            [orgpad.tools.func :as func]
            [orgpad.effects.core :as eff]
            [orgpad.tools.dom :as dom]))

(def ^:protected TreeBeard (js/React.createFactory js/treebeard.Treebeard))

(def ^:protected last-state-init
  {:cursor nil
   :old-unit-tree nil
   :old-js-data nil
   :old-data nil
   :old-status nil
   :old-scroll 0
   :visible-range [0 30]
   :range-updated [-1 -1]})

(defn- update-data
  [component node]
  (when node
    (let [unit-tree (aget node "unit_tree")
          {:keys [orgpad/view-name
                  orgpad/view-type
                  orgpad/view-path]} (:path-info unit-tree)]
      (lc/query component :orgpad/index
                {:view-stack [0 (ot/uid unit-tree) view-name
                              view-type view-path]}
                {:disable-cache? true}))))

(defn- prepare-data
  [component unit-tree data & [idx range]]
  (let [{:keys [view unit]} unit-tree
        info (registry/get-component-info (ot/view-type unit-tree))
        data (or (and data (= (aget data "id") (ot/uid unit-tree)) data)
                 #js {})
        name (or (:orgpad/desc view)
                 (str (:orgpad/view-name info)))
        children-old (aget data "children")
        vrefs (into []
                    (if range
                      (comp
                       (filter (comp not :filtered?))
                       (take (+ (range 1) 10)))
                      (filter (comp not :filtered?)))
                    (:orgpad/refs unit))
        refs-len (count vrefs)
        old-unit-tree (aget data "unit_tree")
        children (if (and children-old
                          (= (.-length children-old) refs-len))
                   children-old
                   (make-array refs-len))]
    (if (:unit unit-tree)
      (doto data
        (aset "id"  (ot/uid unit-tree))
        (aset "unit_tree" unit-tree)
        (aset "name" (str name " " idx))
        (aset "icon" (:orgpad/view-icon info)))
      (when (not old-unit-tree)
        (doto data
          (aset "id" (:db/id unit-tree))
          (aset "name" "Processing..."))))
    (when (not= refs-len 0)
        (aset data "children" children))
    (when (aget data "toggled")
      (loop [idx 0]
        (if (= idx refs-len)
          data
          (let [child-unit-tree (get vrefs idx)
                id (ot/uid child-unit-tree)
                old-data-idx (if children-old
                               (.findIndex children-old
                                           #(and % (= (aget % "id") id)))
                               -1)
                old-data (if children-old
                           (aget children-old old-data-idx)
                           nil)]
            (aset children idx (prepare-data component child-unit-tree
                                             old-data (inc idx)))
            (recur (inc idx))))))
    data))

(defn- find-parent
  [parent node]
  (when parent
    (if (-> parent (aget "children") not)
      nil
      (let [idx (.indexOf (aget parent "children") node)]
        (if (= idx -1)
          (loop [i 0
                 res nil]
            (if (or res (= i (aget parent "children" "length")))
              res
              (recur (inc i) (find-parent (aget parent "children" i) node))))
          parent)))))

(defn- show-unit
  [component last-state node]
  (when-let [parent (find-parent (:old-js-data @last-state) node)]
    (let [view (-> parent (aget "unit_tree") :view)
          unit-tree (aget node "unit_tree")]
      (js/console.log "show-unit" parent view unit-tree)
      (case (:orgpad/view-type view)
      :orgpad/map-view
      (let [vprop (-> unit-tree
                      :props
                      (ot/get-props-view-child (:orgpad/view-name view)
                                               (-> parent (aget "unit_tree") ot/uid)
                                               :orgpad.map-view/vertex-props))]
        (lc/transact! component
                      [[:orgpad.units/map-move-to-unit
                        {:uid (ot/uid unit-tree)
                         :parent-view view
                         :vprop vprop}]]))
      nil
      ))))

(defn- show
  [component node toggled]
  (let [last-state (trum/comp->local-state component true)]
    (when (:cursor @last-state)
      (aset (:cursor @last-state) "active" false))
    (aset node "active" true)
    (vswap! last-state assoc :cursor node)
    (show-unit component last-state node)))

(defn- toggl
  [component toggl-changed? node toggled]
  (let [last-state (trum/comp->local-state component true)]
    (when (aget node "children")
      (aset node "toggled" toggled)
      (vswap! toggl-changed? not))))

(defn- treebeard
  [component data toggl-changed?]
  (TreeBeard #js {:data data
                  :style style/style
                  :onEvent #js {:onHeaderClick (partial show component)
                                :onToggleClick (partial toggl component toggl-changed?)}} nil))

(defn assoc-when-not-exists!
  [a k v]
  (when (-> @a (contains? k) not)
    (swap! a assoc k v)))

(defn- is-vprop?
  [p]
  (= (:orgpad/view-type p) :orgpad.map-view/vertex-props))

(defn- has-vprop?
  [u]
  (some is-vprop? (:props u)))

;; (def position-cache (volatile! nil))

(defn- expand-range
  [selected last-state eunit view-unit recur-level]
  (let [pred (if (= (:orgpad/view-type view-unit) :orgpad/map-view)
               has-vprop?
               (constantly true))
        pred' #(and (or (not selected)
                        (not= recur-level 0)
                        (contains? selected (ot/uid %)))
                    (pred %))]
    (if (= recur-level 0)
      (let [[s e] (:visible-range @last-state)
            position-cache (:position-cache @last-state)
            cnt (- e s)
            from (max 0 (int (- s (/ cnt 2))))
            start (or
                   (when @position-cache
                     (aget @position-cache from))
                   0)
            prefix (when (:old-data @last-state)
                     (subvec (-> @last-state :old-data :unit :orgpad/refs)
                             0 start))
            res [pred' prefix from start (* cnt 2) position-cache]]
        (vswap! last-state assoc :range-updated [from (+ from (* 2 cnt))])
        res)
      [pred' nil 0 0 js/Number.MAX_SAFE_INTEGER nil])))

(defn- determine-visible-nodes
  [component start-idx]
  (when-let [dom-node (-> component rum/state deref
                          (trum/ref-node "index-panel-node"))]
    (let [ref-node (aget (.getElementsByTagName dom-node "ul") 1)]
      (dom/consecutive-visible-nodes (.getBoundingClientRect dom-node)
                                     start-idx ref-node))))

(defn- update-scroll
  [component local-state ev-o-el]
  (let [el (or (.-target ev-o-el) ev-o-el)
        last-state (trum/comp->local-state component true)
        scroll (.-scrollTop el)
        h (-> el .getBoundingClientRect .-height)]
    (vswap! last-state
            update :visible-range
            #(determine-visible-nodes component 0
                                      ;; (-> % first (- 20) (max 0))
                                      ))
    ;; (js/console.log "update-scroll"
    ;;                 (:visible-range @last-state)
    ;;                 (:range-updated @last-state)
    ;;                 (-> @last-state :old-js-data (aget "children") .-length)
    ;;                 (-> @last-state :position-cache deref))
    (when (or (<= (get-in @last-state [:visible-range 0])
                  (get-in @last-state [:range-updated 0]))
              (>= (get-in @last-state [:visible-range 1])
                  (get-in @last-state [:range-updated 1])))
      (swap! local-state assoc :last-scroll scroll))))

(defn- wrap-args
  [[component local-state ev]]
  [component local-state (.-target ev)])

(def ^:private update-scroll-delayed (eff/debounce update-scroll
                                                   250 false wrap-args))

(rum/defcc index-list
  < lc/parser-type-mixin-context (rum/local {:last-scroll 0})
  (trum/no-reactive-local #(merge last-state-init
                                  {:position-cache (volatile! nil)}))
  [component unit-tree status toggl-changed? selected]
  (let [local-state (trum/comp->local-state component)
        last-state (trum/comp->local-state component true)
        {:keys [old-unit-tree old-js-data
                old-data old-status old-scroll
                old-selected]} @last-state
        _ (when (not= old-selected selected)
            (vswap! last-state assoc :position-cache (volatile! nil) :old-data nil))
        data (if (and (= old-unit-tree unit-tree)
                      (= old-scroll (:last-scroll @local-state))
                      (= old-selected selected))
               old-data
               (lc/query component :orgpad/index
                         {:expand-range-fn (partial expand-range selected last-state)}
                         {:disable-cache? true}))
        root-data #js {:id (ot/uid unit-tree) :toggled true}
        js-data (if (and (= old-status status)
                         (= old-scroll (:last-scroll @local-state))
                         (= old-unit-tree unit-tree)
                         (= old-selected selected))
                  old-js-data
                  (prepare-data component data
                                (or (and (= old-selected selected) old-js-data)
                                    root-data)
                                nil (:range-updated @last-state)))]
    (vreset! last-state (merge @last-state
                               {:old-js-data js-data
                                :old-data data
                                :old-unit-tree unit-tree
                                :old-status status
                                :old-selected selected}))
    [:div.index-panel {:onScroll (partial update-scroll component local-state)
                       :ref "index-panel-node"}
     (treebeard component js-data toggl-changed?)]))

(defn- index-search
  [component unit-tree]
  [:div.index-search
   [:span [:input {:id "index-search-input"
                   :type "text"}]]
   [:span.fal.fa-search.search
    {:title "Find"
     :onClick #(let [txt (aget (js/document.getElementById "index-search-input") "value")]
                 (lc/transact! component
                               [[:orgpad.units/filtered-by-pattern
                                 {:unit-tree unit-tree
                                  :params {:selection-text txt}}]]))}]
   [:span.fal.fa-times.cancel
    {:title "Cancel"
     :onClick #(do
                 (aset (js/document.getElementById "index-search-input") "value" "")
                 (lc/transact! component
                               [[:orgpad.units/clear-filtered-pattern
                                 {:unit-tree unit-tree}]]))}]
   [:hr]
   ]
  )

(defn render-index-panel
  [component unit-tree status toggl-changed? selected]
  [:div.index-all
   (index-search component unit-tree)
   (index-list unit-tree status toggl-changed? selected)])

(def ^:private index-eq-fns [identical? identical? = identical? =])
(def ^:private render-index-panel-
  (func/memoize' render-index-panel {:key-fn #(-> % first ot/uid)
                                     :eq-fns index-eq-fns}))

(rum/defcc index-panel < (trum/no-reactive-local false) lc/parser-type-mixin-context
  [component unit-tree app-state]
  (let [toggl-changed? (trum/comp->local-state component true)
        selected (get-in app-state [:filtered (ot/uid unit-tree)])]
    (sidebar/sidebar-component
     :left
     #(let [time-start (js/Date.)
            res (render-index-panel- component unit-tree @toggl-changed? toggl-changed? selected)
            time-end (js/Date.)]
        (js/console.log "index-panel render time" (- time-end time-start) selected)
        res))))
