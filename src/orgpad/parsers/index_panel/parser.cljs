(ns orgpad.parsers.index-panel.parser
  (:require [orgpad.parsers.default-unit :as dp :refer [read mutate]]
            [orgpad.core.store :as store]
            [orgpad.tools.orgpad :as ot]))

(defmethod read :orgpad/index
  [{:keys [query] :as env} _ {:keys [view-stack expand-range-fn]}]
  (js/console.log "read :orgpad/index" view-stack)
  (query (merge env
                {:view-stack view-stack
                 :all-children? true
                 :expand-range-fn expand-range-fn
                 ;; :max-recur-level 1
                 })
         :orgpad/root-view [:orgpad/index]))
