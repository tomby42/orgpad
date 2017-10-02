(ns ^{:doc "Definition of property parser"}
  orgpad.cycle.parser
  (:require [orgpad.core.store :as store]
            [clojure.walk :as walk]))

(defn- parse-query-
  [{:keys [tree read] :as env} k params]

  (let [tree' @tree]
    (vreset! tree #js [])
    (let [val   (read env k params)
          node  #js { :value val
                      :children @tree
                      :key k
                      :params params }]
      (.push tree' node)
      (vreset! tree tree')
      val)))

(defn parse-query
  [state read global-cache k params]

  (let [tree (volatile! #js [])]
    (parse-query- { :state state
                    :query parse-query-
                    :read read
                    :global-cache global-cache
                    :tree tree }
                 k params)
    (-> tree deref (aget 0))))

(defn- mark-changed
  [{:keys [query-changed? force-update-part force-update-all] :as env} node]
  (.forEach (aget node "children") (fn [node] (mark-changed env node)))
  (let [me-changed?   (or (query-changed? node env @force-update-part) @force-update-all)]
    (doto node
      (aset "me-changed?" me-changed?)
      (aset "changed?" (or me-changed?
                           (.some (aget node "children") (fn [n] (aget n "changed?"))))))))

(defn clone-node
  [node]
  (let [children (aget node "children")]
    #js { :value (aget node "value")
          :children (amap children i ret (clone-node (aget children i)))
          :key (aget node "key")
          :params (aget node "params") }))

(defn- update-parsed-query-
  [{:keys [read tree] :as env} & [key params]]
  (let [dtree    @tree
        old-tree (aget dtree 0)
        tree'    (aget dtree 1)
        node     (or (.shift old-tree) #js {:me-changed true :key key :params params :children #js [] :value {}})]
    ;; (js/console.log "update-parsed-query-")
    ;; (js/console.log node)
    ;; (js/console.log old-tree)
    ;; (js/console.log tree)
    ;; (when (nil? node)
    ;;   (js* "debugger;"))
    (if (aget node "me-changed?")
      (do
        (vreset! tree #js [])
        (let [val (parse-query- (merge env { :query parse-query- :old-node node })
                                (aget node "key") (aget node "params"))]
          (vreset! tree #js [old-tree (.concat tree' @tree)])
          val))
      (if (aget node "changed?")
        (do
          (vreset! tree #js [(aget node "children") #js []])
          (let [val (read env (aget node "key") (aget node "params"))]
            (doto node
              (aset "value" val)
              (aset "children" (-> tree deref (aget 1))))
            (.push tree' node)
            (vreset! tree #js [old-tree tree'])
            val))
        (do
          (.push tree' node)
          (vreset! tree #js [old-tree tree'])
          (aget node "value"))))))

(defn- to-js
  [c]
  (clj->js
   (walk/postwalk (fn [c']
                    (if (set? c')
                      (into {} (map #(vector % true)) c')
                      c')) c)))

(defn update-parsed-query
  [state read old-tree changed? force-update-all force-update-part global-cache]
  (let [tree (volatile! #js [#js [(mark-changed { :query-changed? changed?
                                                  :force-update-all force-update-all
                                                  :force-update-part force-update-part
                                                  :changed-entities (to-js (store/changed-entities state))
                                                  :state state } old-tree)] #js []])]
    (vreset! force-update-all false)
    (vreset! force-update-part {})
    (update-parsed-query- { :state state
                            :query update-parsed-query-
                            :read read
                            :global-cache global-cache
                            :tree tree })
    (-> tree deref (aget 1 0))))

(defn force-update!
  [force-update-all force-update-part & [uid]]
  (if uid
    (vswap! force-update-part assoc uid true)
    (vreset! force-update-all true)))
