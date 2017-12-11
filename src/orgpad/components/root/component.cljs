(ns ^{:doc "Root component"}
  orgpad.components.root.component
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.cycle.life :as lc]
            [cemerick.url :as url]
            [orgpad.components.registry :as registry]
            [orgpad.components.node :as node]
            [orgpad.components.sidebar.sidebar :as sidebar]
            [orgpad.components.input.file :as if]
            [orgpad.components.root.toolbar :as tbar]
            [orgpad.components.root.nesting :as nest]))

(rum/defcc root-component < lc/parser-type-mixin-context
  [component]
  (let [unit-tree (lc/query component :orgpad/root-view [])
        app-state (lc/query component :orgpad/app-state [])]
    [ :div.root-view
      ;; (rum/with-key (sidebar/sidebar-component) 0)
      ;(js/console.log "Unit-tree: " unit-tree)
      ;(js/console.log "App-state: " app-state)
      (rum/with-key (node/node unit-tree app-state) "root-view-part")
      (rum/with-key (tbar/status unit-tree app-state) "status-part")
      (rum/with-key (nest/nesting unit-tree) "nesting-part")
      (when (app-state :loading)
        [ :div.loading
         [ :div.status
          [ :i.fa.fa-spinner.fa-pulse.fa-3x.fa-fw.margin-bottom ]
          [ :div.sr-only "Loading..." ] ]
         ]
        )
     ] ) )

(registry/register-component-info
 :orgpad/root-view
 {:orgpad/default-view-info   { :orgpad/view-type :orgpad/map-view
                                :orgpad/view-name "default" }
  :orgpad/class               root-component
  :orgpad/needs-children-info true

  :orgpad/left-toolbar [
    [{:elem :roll
      :id "file"
      :icon "far fa-save"
      :label "File"
      :roll-items [
       {:id "save"
        :icon "far fa-download"
        :label "Save"
        :on-click #(lc/transact! (:component %1) [[ :orgpad/save-orgpad true ]]) }
       {:load-files true
        :id "load"
        :icon "far fa-upload"
        :label "Load"
        :on-click #(lc/transact! (:component %1) [[ :orgpad/load-orgpad %2 ]]) } 
       {:id "tohtml"
        :label "Export HTML"
        :on-click #(lc/transact! (:component %1) [[ :orgpad/export-as-html ((lc/global-conf (:component %1)) :storage-el) ]]) }
       ]}]
    [
     {:elem :btn
      :id "history"
      :icon "far fa-clock"
      :title "History on/off"
      :on-click #(swap! (:local-state %1) update :history not)
      :disabled #(not (or (lc/query (:component %1) :orgpad/undoable? [] true)
                      (lc/query (:component %1) :orgpad/redoable? [] true)))
      :hidden true}
      ;:hidden #(= (:mode %1) :read)}
     {:elem :btn
      :id "undo"
      :icon "far fa-undo-alt"
      :title "Undo"
      :on-click #(lc/transact! (:component %1) [[ :orgpad/undo true ]])
      :disabled #(not (lc/query (:component %1) :orgpad/undoable? [] true))
      :hidden #(= (:mode %1) :read)}
     {:elem :btn
      :id "redo"
      :icon "far fa-redo-alt"
      :title "Redo"
      :on-click #(lc/transact! (:component %1) [[ :orgpad/redo true ]])
      :disabled #(not (lc/query (:component %1) :orgpad/redoable? [] true))
      :hidden #(= (:mode %1) :read)}
     ]
  ]

  :orgpad/right-toolbar [
    [{:elem :btn
      :id "level-up"
      :icon "far fa-sign-out-alt"
      :title "Leave current unit"
      :on-click #(lc/transact! (:component %1)
                   [[:orgpad/root-unit-close {
                       :db/id (:id %1)
                       :orgpad/view-name ((:view %1) :orgpad/view-name)
                       :orgpad/view-type ((:view %1) :orgpad/view-type)
                       :orgpad/view-path ((:path-info %1) :orgpad/view-path) }]])
      :hidden #(= (:id %1) 0)}
    ]
    [{:elem :btn
      :id "edit-mode"
      :icon "far fa-pencil"
      :title "Edit mode"
      :active #(= (:mode %1) :write)
      :on-click #(lc/transact! (:component %1) [[:orgpad/app-state [[:mode] :write]]]) }
     {:elem :btn
      :id "read-mode"
      :icon "far fa-eye"
      :title "Read mode"
      :active #(= (:mode %1) :read)
      :on-click #(lc/transact! (:component %1) [[:orgpad/app-state [[:mode] :read]]]) }
    ]
    [{:elem :btn
      :id "help"
      :icon "far fa-question-circle"
      :label "Help"
      :on-click #(js/window.open (str                        
                                   (url/url (aget js/window "location" "href"))
                                   "?u=http://pavel.klavik.cz/orgpad/help.orgpad") "_blank")}
  ]]
})
