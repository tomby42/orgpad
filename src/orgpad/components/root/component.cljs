(ns ^{:doc "Root component"}
  orgpad.components.root.component
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.cycle.life :as lc]
            [orgpad.components.registry :as registry]
            [orgpad.components.node :as node]
            [orgpad.components.sidebar.sidebar :as sidebar]
            [orgpad.components.input.file :as if]
            [orgpad.components.root.status :as st]))

(rum/defcc root-component < lc/parser-type-mixin-context [component]
  (let [unit-tree (lc/query component :orgpad/root-view [])
        app-state (lc/query component :orgpad/app-state [])]
    [ :div { :className "root-view" }
      ;; (rum/with-key (sidebar/sidebar-component) 0)
      (rum/with-key (node/node unit-tree app-state) "root-view-part")
      (rum/with-key (st/status unit-tree app-state) "status-part")
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

  :orgpad/toolbar [
    [{:elem :roll
      :id "file"
      :icon "far fa-save"
      :label "File"
      :roll-items [
       {:id "save"
        :icon "far fa-download"
        :label "Save"
        :on-mouse-down #(lc/transact! {:component %1} [[ :orgpad/save-orgpad true ]]) }
        :on-mouse-down #(js/console.log (pr-str (:component %1))) }
       {:elem :load
        :id "load"
        :icon "far fa-upload"
        :label "Load"
        :on-mouse-down #(lc/transact! {:component %1} [[ :orgpad/load-orgpad %2 ]]) } 
       {:id "tohtml"
        :label "Export HTML"
        :on-mouse-down #(lc/transact! {:component %} [[ :orgpad/export-as-html ((lc/global-conf {:component %}) :storage-el) ]]) }
       ]}]]
  })
