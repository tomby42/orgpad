(ns ^{:doc "Node component"}
  orgpad.components.node
  (:require [rum.core :as rum]
            [orgpad.cycle.life :as lc]
            [orgpad.components.registry :as registry]))

(rum/defc node < rum/static lc/parser-type-mixin [unit-tree & args]
  (try
    (let [type (-> unit-tree :view :orgpad/view-type)
          child-info (registry/get-component-info type)
          child (child-info :orgpad/class)]
      (apply child unit-tree args))
    (catch :default e
      (js/console.log "rendering error" e) ;; TODO - show error message
      nil)))
