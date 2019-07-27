(ns ^{:doc "Atom editor"}
  orgpad.components.atomic.atom-editor
  (:require [rum.core :as rum]
            [orgpad.tools.rum :as trum]
            [orgpad.cycle.life :as lc]
            [orgpad.components.editors.tinymce :as tinymce]))

(defn eq-names
  [v1 v2]
  (= (:orgpad/view-name v1) (:orgpad/view-name v2)))

(defn try-resize-editor
  [state]
  (let [node (trum/ref-node state "editor-node")
        iframe (-> node (.getElementsByTagName "IFRAME") (aget 0))]
    (when iframe
      (aset iframe "style" "height" (str (-> state :rum/args last) "px")))))

(rum/defcc atom-editor < (trum/statical [= eq-names (constantly true) = =])
  lc/parser-type-mixin-context
  (trum/gen-update-mixin try-resize-editor)
  [component unit-id view atom & [cfg-type height]]
  (let [cfg (case cfg-type
              :inline tinymce/default-config-simple-inline
              :quick tinymce/default-config-quick
              tinymce/default-config-full)]
    (when height (aset cfg "height" height))
    [:div {:key (str unit-id "-" (view :orgpad/view-name))
           :ref "editor-node"}
     (tinymce/tinymce atom
                      (fn [e]
                        (let [target (aget e "target")]
                          (lc/transact!
                           component
                           [[:orgpad.atom/update
                             {:db/id unit-id
                              :orgpad/view view
                              :orgpad/atom (.call (aget target "getContent") target)}]]))) cfg)]))
