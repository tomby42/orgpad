(ns ^{:doc "Atom editor"}
  orgpad.components.atomic.atom-editor
  (:require [rum.core :as rum]
            [orgpad.tools.rum :as trum]
            [orgpad.cycle.life :as lc]
            [orgpad.components.editors.tinymce :as tinymce]))

(rum/defcc atom-editor < (trum/statical [= = (constantly true)]) lc/parser-type-mixin-context
  [component unit-id view atom]
  [ :div { :key (str unit-id (view :orgpad/view-name)) }
    (tinymce/tinymce atom
                     (fn [e]
                       (let [target (aget e "target")
                             view' (second (trum/comp->args component))]
                         (lc/transact!
                          component
                          [[:orgpad.atom/update
                            { :db/id unit-id
                              :orgpad/view view'
                              :orgpad/atom (.call (aget target "getContent") target) } ]] )))) ] )
