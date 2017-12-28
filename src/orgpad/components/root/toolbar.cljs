(ns ^{:doc "Main toolbar"}
  orgpad.components.root.toolbar
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [cljsjs.react-select]
            [orgpad.cycle.life :as lc]
            [orgpad.tools.dscript :as ds]
            [orgpad.components.registry :as registry]
            [orgpad.components.menu.toolbar.component :as tbar]
            [orgpad.tools.orgpad :as ot]
            [orgpad.tools.orgpad-manipulation :as omt]
            [orgpad.tools.js-events :as jev]
            [orgpad.tools.rum :as trum]
            [orgpad.components.ci.dialog :as ci]))

(def ^:private mode-icons
  { :read  "fa-eye"
    :write "fa-pencil" })

(defn- next-mode
  [current-mode]
  (case current-mode
    :read  :write
    :write :read))

(defn- list-of-view-names
  [unit view-type]
  (->> unit
       :orgpad/props-refs
       (filter :orgpad/view-name)
       (filter #(= (% :orgpad/view-type) view-type))
       (map :orgpad/view-name)
       (cons "default")
       set
       (map (fn [n] #js { :value n :label n }))
       into-array))

(defn- render-view-names
  [component {:keys [unit view] :as unit-tree} local-state]
  (let [current-name (view :orgpad/view-name)
        list-of-view-names (list-of-view-names unit (view :orgpad/view-type))]
    (js/React.createElement js/Select
                            #js { :value current-name
                                  :options list-of-view-names
                                  :onInputChange #(do (swap! local-state merge { :typed % }) %)
                                  :onChange (fn [ev]
                                              (lc/transact! component
                                                            [[:orgpad/root-view-conf [unit-tree
                                                                                      { :attr :orgpad/view-name
                                                                                        :value (.-value ev) }]]]))
                                 })))

(defn- list-of-view-types
  []
  (->> (dissoc (registry/get-registry) :orgpad/root-view)
       (map (fn [[view-type info]] #js { :key view-type
                                         :value (info :orgpad/view-name)
                                         :label (info :orgpad/view-name)
                                         :icon (info :orgpad/view-icon) }))
       into-array))

(defn- render-view-types
  [component {:keys [view] :as unit-tree}]
  (let [list-of-view-types (list-of-view-types)
        current-type (-> view :orgpad/view-type registry/get-component-info :orgpad/view-name)]
    (js/React.createElement js/Select
                            #js { :value current-type
                                  :options list-of-view-types
                                  :clearable false
                                  :searchable false
                                  :onChange (fn [ev]
                                              (lc/transact! component
                                                            [[:orgpad/root-view-conf [unit-tree
                                                                                      { :attr :orgpad/view-type
                                                                                        :value (.-key ev) }]]]))
                                 })))

;(defn- render-view-menu
;  [component unit-tree local-state]
;  (render-menu
;   local-state :view-menu-unroll "View" { :body "view-menu-body"
;                                          :all "view-menu-all"
;                                          :header "view-menu-header"
;                                          :open "open-view" }
;        [ :div { :className "view-name" }
;         (render-view-names component unit-tree local-state)
;         [ :span { :className "far fa-plus-circle view-name-add"
;                   :title "New view"
;                   :onClick #(lc/transact! component
;                                           [[:orgpad/root-new-view [unit-tree
;                                                                    { :attr :orgpad/view-name
;                                                                      :value (@local-state :typed) }]]]) } ] ]
;        [ :div { :className "view-type" }
;         (render-view-types component unit-tree) ]))

(defn- normalize-range
  [min max val]
  (-> (if (= val "") "0" val)
      js/parseInt
      (js/Math.max min)
      (js/Math.min max)))


(defn- render-history
  [component local-state]
  (let [[h-finger h-cnt] (lc/query component :orgpad/history-info [] true)]
    [ :div.history-slider
     [ :input { :type "range" :min -1 :max (dec h-cnt) :step 1 :value (or h-finger (dec h-cnt))
                :style { :width (* h-cnt 10) }
                :onMouseDown jev/stop-propagation
                :onBlur jev/stop-propagation
                :onChange #(lc/transact! component
                                         [[ :orgpad/history-change
                                            { :old-finger (or h-finger h-cnt)
                                              :new-finger (normalize-range -1 h-cnt
                                                                           (-> % .-target .-value))}]])
               }]]))

(defn- gen-view-toolbar 
  [{:keys [unit view] :as unit-tree} view-type]
  (let [view-toolbar (-> view :orgpad/view-type registry/get-component-info :orgpad/toolbar)]
    (if (and (= view-type :orgpad/map-tuple-view) (not (ot/no-sheets? unit-tree)))
      (let [ac-unit-tree (ot/active-child-tree unit view)
            ac-view-types-roll (tbar/gen-view-types-roll (:view ac-unit-tree) :ac-unit-tree "Current page" "page-views" #(= (:mode %1) :read))
            last-sec (- (count view-toolbar) 1) ] 
        (update-in view-toolbar [last-sec] conj ac-view-types-roll ))
      view-toolbar)))

(rum/defcc status < rum/reactive (rum/local { :unroll false :view-menu-unroll false :typed "" :history false }) lc/parser-type-mixin-context
  [component { :keys [unit view path-info] :as unit-tree } app-state node-state]
  (let [id (unit :db/id)
        local-state (trum/comp->local-state component)
        msg-list (lc/query component :orgpad.ci/msg-list [])
        view-type (ot/view-type unit-tree)]
    [:div {:onMouseDown jev/block-propagation :onTouchStart jev/block-propagation}
     (when (:history @local-state)
       (render-history component local-state))

     ;(ci/dialog-panel unit-tree app-state msg-list)

       (let [root-component-left-toolbar (-> :orgpad/root-view registry/get-component-info :orgpad/left-toolbar)
             view-types-section [(tbar/gen-view-types-roll view :unit-tree "Current" "views" #(= (:mode %1) :read))]
             view-toolbar (gen-view-toolbar unit-tree view-type)
             left-toolbar (concat (conj root-component-left-toolbar view-types-section) view-toolbar)
             right-toolbar (-> :orgpad/root-view registry/get-component-info :orgpad/right-toolbar)
             params { :id           id
                      :unit-tree    unit-tree 
                      :unit         unit
                      :view         view
                      :path-info    path-info
                      :local-state  local-state
                      :node-state   node-state
                      :mode         (:mode app-state)
                      :ac-unit-tree (when (= view-type :orgpad/map-tuple-view) (ot/active-child-tree unit view))
                      :ac-view-type (when (= view-type :orgpad/map-tuple-view) (ot/view-type (ot/active-child-tree unit view))) }
             ]
         (tbar/toolbar "toolbar" params left-toolbar right-toolbar))
         ]
         
         
         ) )
