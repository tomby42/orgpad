(ns ^{:doc "Atom editor"}
  orgpad.components.atomic.atom-editor
  (:require [rum.core :as rum]
            [orgpad.tools.rum :as trum]
            [orgpad.cycle.life :as lc]
            [orgpad.components.editors.tinymce :as tinymce]))

(defn eq-names
  [v1 v2]
  (= (:orgpad/view-name v1) (:orgpad/view-name v2)))

(rum/defcc atom-editor < (trum/statical [= eq-names (constantly true) = =]) lc/parser-type-mixin-context
  [component unit-id view atom & [cfg-type height]]
  (let [cfg (case cfg-type
              :inline tinymce/default-config-simple-inline
              :quick tinymce/default-config-quick
              tinymce/default-config-full)]
    (when height (aset cfg "height" height))
    [:div {:key (str unit-id "-" (view :orgpad/view-name))}
     (tinymce/tinymce atom
                      (fn [e]
                        (let [target (aget e "target")]
                          (lc/transact!
                           component
                           [[:orgpad.atom/update
                             {:db/id unit-id
                              :orgpad/view view
                              :orgpad/atom (.call (aget target "getContent") target)}]]))) cfg)]))
