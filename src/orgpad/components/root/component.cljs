(ns ^{:doc "Root component"}
  orgpad.components.root.component
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.tools.rum :as trum]
            [orgpad.cycle.life :as lc]
            [cemerick.url :as url]
            [orgpad.components.registry :as registry]
            [orgpad.components.node :as node]
            [orgpad.components.sidebar.sidebar :as sidebar]
            [orgpad.components.input.file :as if]
            [orgpad.components.root.toolbar :as tbar]
            [orgpad.components.root.nesting :as nest]
            [orgpad.tools.orgpad :as ot]
            [orgpad.components.ci.dialog :as ci]
            [orgpad.components.root.settings :as settings]
            [orgpad.components.editors.styles :as styles]))

;; TODO: hack!! We need to think about passing custom params to children and/or local states in app state
;; regarding to render hierarchy.
(defn- update-node-component
  [component unit-tree local-state]
  (let [c (lc/get-global-cache component (ot/uid unit-tree) "component")]
    (when (and c (.-context c) (not= (:component @local-state) c))
      (let [state (trum/comp->local-state c)]
        (add-watch state :root-component-update (fn [_ _ old-state new-state]
                                                  (if (not= (:canvas-mode old-state) (:canvas-mode new-state))
                                                    (rum/request-render component))))

        (swap! local-state assoc :component c)
        (swap! local-state assoc :node-state state)))))

(defn- status
  [unit-tree app-state local-state]
  [:div {:className (str "root-toolbar " (if (:root-toolbar-visible @local-state) "" "hide"))}
   (tbar/status unit-tree app-state local-state)
   [:span.root-toolbar-handle {:onClick #(swap! local-state update :root-toolbar-visible not)}
    [:i {:className (str "fa fa-lg "
                         (if (:root-toolbar-visible @local-state) "fa-angle-up" "fa-angle-down"))}]]])

(def default-values {:component nil
                     :node-state nil
					 :show-settings false
					 :show-styles-editor false
					 :root-toolbar-visible true})

(rum/defcc root-component < lc/parser-type-mixin-context (rum/local default-values)
 [component]
 (let [unit-tree (lc/query component :orgpad/root-view [])
  app-state (lc/query component :orgpad/app-state [])
  msg-list (lc/query component :orgpad.ci/msg-list [])
  local-state (trum/comp->local-state component)] ;; local-state contains children component or nil

  (js/setTimeout #(update-node-component component unit-tree local-state) 100)

  [ :div.root-view
  ;; (rum/with-key (sidebar/sidebar-component) 0)
  (rum/with-key (node/node unit-tree app-state) "root-view-part")
  (status unit-tree app-state local-state)
  (rum/with-key (nest/nesting unit-tree) "nesting-part")
  (when (:enable-experimental-features? app-state)
   (rum/with-key (ci/dialog-panel unit-tree app-state msg-list) "ci-part"))
  (when (:show-settings @local-state)
   (rum/with-key (settings/settings app-state #(swap! local-state assoc :show-settings false)) "settings"))
  (when (:show-styles-editor @local-state)
   (rum/with-key (styles/styles-editor app-state #(swap! local-state assoc :show-styles-editor false)) "styles"))
  (when (:loading app-state)
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

   :orgpad/left-toolbar
   [
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
   {:load-files true
	   :id "import"
		   :icon "far fa-code-merge"
		   :label "Import"
		   :on-click #(lc/transact! (:component %1) [[ :orgpad/import-orgpad %2 ]]) }
   {:id "tohtml"
	   :label "Export HTML"
		   :on-click #(lc/transact! (:component %1) [[ :orgpad/export-as-html ((lc/global-conf (:component %1)) :storage-el) ]]) }
   ]}]
	   [{:elem :roll
		   :id "styles-editor"
			   :icon "far fa-calendar"
			   :label "Styles"
			   :roll-items
			   [{:id "edit-styles"
				   :icon "far fa-edit"
					   :label "Edit"
					   :on-click #(swap! (:root-local-state %1) assoc :show-styles-editor true)}]}]
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
			[{:elem :roll
				:id "debug"
					:icon "far fa-bug"
					:label "Debug"
					:hidden #(not= (-> %1 :app-state :enable-experimental-features?) true)
					:roll-items [{:id "swap-all-links"
						:icon "far fa-exchange"
							:label "Swap All Links"
							:on-click #(lc/transact! (:component %1) [[:orgpad/debug-swap-all-links true]])}]}]
							[{:elem :btn
								:id "settings"
									:label "Settings"
									:icon "fa fa-cog"
									:on-click #(swap! (:root-local-state %1) assoc :show-settings true)
							}
     {:elem :btn
      :id "help"
      :icon "far fa-question-circle"
      :label "Help"
      :on-click #(js/window.open "help.html" "_blank")}
  ]]
})
