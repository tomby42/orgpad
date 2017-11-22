(ns ^{:doc "Root component"}
  orgpad.components.root.status
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [cljsjs.react-select]
            [orgpad.cycle.life :as lc]
            [orgpad.tools.dscript :as ds]
            [orgpad.components.registry :as registry]
            [orgpad.components.input.file :as if]
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
                                         :label (info :orgpad/view-name) }))
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

(defn- render-menu
  [local-state menu-key label styles & items]
  [ :div { :className (str "menu " (styles :all)) }
   [ :span { :className (str "menu-header " (styles :header))
             :onClick #(js/setTimeout (fn [] (swap! local-state update menu-key not)) 100) }
    [ :span (str label " ")
     [ :i {:className (str "fa " (if (@local-state menu-key) "fa-caret-up" "fa-caret-down")) }] ] ]
   (into
    [ :ul { :className (str "menu-body " (styles :body) " " (when (@local-state menu-key) (styles :open))) } ]
    (map (fn [item] [ :li item ]) items)) ])

(defn- render-view-menu
  [component unit-tree local-state]
  (render-menu
   local-state :view-menu-unroll "View" { :body "view-menu-body"
                                          :all "view-menu-all"
                                          :header "view-menu-header"
                                          :open "open-view" }
        [ :div { :className "view-name" }
         (render-view-names component unit-tree local-state)
         [ :span { :className "fa fa-plus-circle view-name-add"
                   :title "New view"
                   :onClick #(lc/transact! component
                                           [[:orgpad/root-new-view [unit-tree
                                                                    { :attr :orgpad/view-name
                                                                      :value (@local-state :typed) }]]]) } ] ]
        [ :div { :className "view-type" }
         (render-view-types component unit-tree) ]))

(defn- render-file-menu
  [component unit-tree local-state]
  (render-menu
   local-state :file-menu-unroll "File" { :body "file-menu-body"
                                          :all "file-menu-all"
                                          :header "file-menu-header"
                                          :open "open-file" }

   [ :div.file-item
    { :onClick #(lc/transact! component [[ :orgpad/save-orgpad true ]]) }
    [ :span "Save" ]]

   [ :div.file-item
    (if/file-input { :on-change #(lc/transact! component [[ :orgpad/load-orgpad % ]]) }
                   [ :span { :key 1 } "Load" ])]

   [ :div.file-item
    { :onClick #(lc/transact! component [[ :orgpad/export-as-html ((lc/global-conf component) :storage-el) ]]) }
    [ :span "Export html" ]]
   ))

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

(defn- render-edit-menu
  [component unit-tree local-state]
  (let [undoable (lc/query component :orgpad/undoable? [] true)
        redoable (lc/query component :orgpad/redoable? [] true)]
    (render-menu
     local-state :edit-menu-unroll "Edit" { :body "edit-menu-body"
                                            :all "edit-menu-all"
                                            :header "edit-menu-header"
                                            :open "open-edit" }

     [ :div
      (if undoable
        { :className "file-item" :onClick #(lc/transact! component [[ :orgpad/undo true ]]) }
        { :className "disabled-item" })
      [ :span "Undo" ]]

     [ :div
      (if redoable
        { :className "file-item" :onClick #(lc/transact! component [[ :orgpad/redo true ]]) }
        { :className "disabled-item"})
      [ :span  "Redo" ]]

     [ :div
      (if (or undoable redoable)
        { :className "file-item" :onClick #(swap! local-state update :history not) }
        { :className "disabled-item"})
      [ :span  (str "History " (if (:history @local-state) "off" "on")) ]]
     )))

(rum/defcc status < (rum/local { :unroll false :view-menu-unroll false :typed "" :history false }) lc/parser-type-mixin-context
  [component { :keys [unit view path-info] :as unit-tree } app-state]
  (let [id (unit :db/id)
        local-state (trum/comp->local-state component)
        msg-list (lc/query component :orgpad.ci/msg-list [])]
    [:div {:onMouseDown jev/block-propagation :onTouchStart jev/block-propagation}
     (when (:history @local-state)
       (render-history component local-state))

     (ci/dialog-panel unit-tree app-state msg-list)

     [ :div { :className "status-menu" }
      [ :div { :className "tools-menu" :title "Actions" }
       [ :div { :className "tools-button"
                :onClick #(js/setTimeout
                           (fn []
                             (swap! local-state update-in [:unroll] not)) 100) }
        [ :i { :className "fa fa-navicon fa-lg" } ] ]
       [ :div { :className (str "tools" (when (@local-state :unroll) " more-current")) }
        (render-file-menu component unit-tree local-state)
        (render-edit-menu component unit-tree local-state)
        (render-view-menu component unit-tree local-state)

;;       [ :div { :className "mode-button" }
;;        [ :i { :className (str "fa fa-leaf fa-lg") } ] ]
        ]
       ]

      [ :div { :className "mode-button"
               :title "Toggle mode"
               :onClick #(lc/transact!
                          component
                          [[:orgpad/app-state
                            [[:mode] (next-mode (:mode app-state))]]]) }
       [ :i { :className (str "fa "  (mode-icons (:mode app-state)) " fa-lg") } ] ]


      (when (not= id 0)
        [ :div { :className "done-root-unit-button"
                 :title "Done"
                 :onClick #(lc/transact!
                            component
                            [[:orgpad/root-unit-close { :db/id id
                                                        :orgpad/view-name (view :orgpad/view-name)
                                                        :orgpad/view-type (view :orgpad/view-type)
                                                        :orgpad/view-path (path-info :orgpad/view-path) }]])}
         [ :i { :className "fa fa-check-circle-o fa-lg" } ] ] ) ] ] ) )
