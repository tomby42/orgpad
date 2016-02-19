(ns try.p1
  (:require [orgpad.core.orgpad :as o]
            [orgpad.core.store :as s]
            [om.next :as om]
            [orgpad.parsers.default :as dp]
            [orgpad.components.queries :as qs]
            [orgpad.parsers.default.root :as drp]
            [orgpad.components.atomic.component :as ac]))

(def o1 (atom (o/empty-orgpad-db)))
(def my-parser (om/parser {:read dp/read}))

(defn p1-parser []
  (println (my-parser {:state o1} [{:orgpad/root-view [{:unit qs/unit-query} {:view qs/unit-view-query}]}])))
