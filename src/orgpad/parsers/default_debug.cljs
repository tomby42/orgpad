(ns ^{:doc "Default debug parser"}
  orgpad.parsers.default-debug
  (:require [com.rpl.specter :refer [keypath]]
            [orgpad.core.store :as store]
            [orgpad.core.orgpad :as orgpad]
            [orgpad.parsers.default-unit :as dp :refer [read mutate updated?]]
            [orgpad.tools.dscript :as ds]
            [orgpad.tools.orgpad :as ot]
            [orgpad.tools.geocache :as geocache]
            [orgpad.tools.jcolls :as jscolls]
            [orgpad.tools.dom :as dom]
            [orgpad.components.registry :as registry]))

(defmethod mutate :orgpad/debug-swap-all-links
  [{:keys [state]} _ _]
  (let [refs-orders (store/query state '[:find ?uid ?refs-order
                                         :in $
                                         :where
                                         [?uid :orgpad/refs-order ?refs-order]
                                         [?uid :orgpad/props-refs ?p]
                                         [?p :orgpad/view-type :orgpad.map-view/link-props]])
        qry (into [] (mapcat (partial apply ot/make-swap-refs-order-qry)) refs-orders)]
    {:state (store/transact state qry)}))
