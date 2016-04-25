(ns ^{:doc "Definition of map view parser"}
  orgpad.parsers.map.parser
  (:require [orgpad.core.store :as store]
            [orgpad.effects.core :as eff]
            [orgpad.parsers.default :as dp :refer [read mutate]]))
