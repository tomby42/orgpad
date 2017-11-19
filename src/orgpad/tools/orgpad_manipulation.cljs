(ns ^{:doc "Orgpad manipulation tools"}
  orgpad.tools.orgpad-manipulation
  (:require [orgpad.core.store :as store]
            [orgpad.tools.orgpad :as ot]
            [orgpad.tools.dscript :as dscript]
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

