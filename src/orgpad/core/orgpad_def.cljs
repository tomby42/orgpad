(ns orgpad.core.orgpad-def)

(def orgpad-db-schema
  {:orgpad/type        {}
   :orgpad/atom        {}
   :orgpad/tags        {:db/cardinality :db.cardinality/many}
   :orgpad/desc        {}
   :orgpad/refs        {:db/valueType :db.type/ref
                        :db/cardinality :db.cardinality/many}
   :orgpad/props-refs  {:db/valueType :db.type/ref
                        :db/cardinality :db.cardinality/many}
   :orgpad/view-stack  {:db/cardinality :db.cardinality/many}
   :orgpad/view-name   {}
   :orgpad/view-type   {}
   :orgpad/view-path   {}
   :orgpad/transform   {}
   :orgpad/active-unit {}
   :orgpad/unit-position {}
   :orgpad/unit-visibility {}
   :orgpad/unit-width  {}
   :orgpad/unit-height {}
   :orgpad/unit-max-width {}
   :orgpad/unit-max-height {}
   :orgpad/unit-border-color {}
   :orgpad/unit-bg-color {}
   :orgpad/unit-border-width {}
   :orgpad/unit-corner-x {}
   :orgpad/unit-corner-y {}
   :orgpad/unit-border-style {}
   :orgpad/unit-padding {}
   :orgpad/unit-autoresize? {}
   :orgpad/unit-autoresize-ratio {}
   :orgpad/link-color {}
   :orgpad/link-width {}
   :orgpad/link-dash {}
   :orgpad/link-mid-pt {}
   :orgpad/link-directed {}
   :orgpad/link-arrow-pos {}
   :orgpad/refs-order  {}
   :orgpad/text        {}
   :orgpad/response    {}
   :orgpad/parameters  {}
   :orgpad/independent {}
   :orgpad/view-style {}
   :orgpad/style-name {}
   :orgpad/context-unit {}})

(def root-entity-id 1)
