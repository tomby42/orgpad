(ns ^{:doc "Orgpad manipulation tools"}
  orgpad.tools.orgpad-manipulation
  (:require [orgpad.core.store :as store]
            [orgpad.tools.orgpad :as ot]
            [orgpad.tools.dscript :as dscript]
            [orgpad.tools.js-events :as jev]
            [orgpad.tools.geom :as geom]
            [orgpad.cycle.life :as lc]))

(defn change-view-type
  [component unit-tree view-type]
	(lc/transact! component 
	  [[:orgpad/root-view-conf [unit
      { :attr :orgpad/view-type
		 	  :value view-type }]]])
)

(defn switch-active-sheet
  [component unit-tree dir]
  (lc/transact! component [[ :orgpad.sheet/switch-active
                            { :unit-tree unit-tree
                              :direction dir
                              :nof-sheets (ot/refs-count unit) } ]]))

(defn new-sheet
  [component unit-tree]
  (lc/transact! component [[ :orgpad.units/new-sheet unit-tree ]]))

(defn remove-active-sheet
  [component unit-tree]
  (lc/transact! component [[ :orgpad.units/remove-active-sheet-unit unit-tree ]]))

(defn start-link
  [local-state ev]
  (swap! local-state merge { :local-mode :make-link
                             :link-start-x (.-clientX (jev/touch-pos ev))
                             :link-start-y (.-clientY (jev/touch-pos ev))
                             :mouse-x (.-clientX (jev/touch-pos ev))
                             :mouse-y (.-clientY (jev/touch-pos ev)) }))


(defn remove-unit
  [component id]
  (lc/transact! component [[ :orgpad.units/remove-unit
                             id ]]))


(defn open-unit
  [component { :keys [unit view path-info] }]
  (let [{ :keys [orgpad/view-name orgpad/view-type] } view
        view-path (path-info :orgpad/view-path)]
    (lc/transact! component [[ :orgpad/root-view-stack { :db/id (unit :db/id)
                                                         :orgpad/view-name view-name
                                                         :orgpad/view-type view-type
                                                         :orgpad/view-path view-path } ]])))
