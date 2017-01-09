(ns ^{:doc "Definition of property parser"}
    orgpad.cycle.parser
  (:require [orgpad.core.store :as store]
            [clojure.walk :as walk]))

(defn- parse-props-
  [{:keys [tree read] :as env} k params]

  (let [tree' @tree]
    (vreset! tree [])
    (let [val   (read env k params)
          node  { :value val
                  :children @tree
                  :key k
                  :params params }]
      (vreset! tree (conj tree' node))
      val)))

(defn- parse-props-1-
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

(defn parse-props
  [state read k params]

  (let [tree (volatile! [])]
    (parse-props- { :state state
                    :props parse-props-
                    :read read
                    :tree tree }
                 k params)
    (-> tree deref first)))

(defn parse-props-1
  [state read k params]

  (let [tree (volatile! #js [])]
    (parse-props-1- { :state state
                      :props parse-props-1-
                      :read read
                      :tree tree }
                 k params)
    (-> tree deref (aget 0))))

(defn- mark-changed
  [{:keys [props-changed? force-update-part force-update-all] :as env} node]
  (let [node'         (assoc node :children (mapv (fn [node] (mark-changed env node)) (node :children)))
        me-changed?   (or (props-changed? node' env @force-update-part) @force-update-all)]

    (-> node'
        (assoc :me-changed? me-changed?
               :changed? (or me-changed?
                             (some :changed? (node' :children))
                             false))) ))

(defn- mark-changed-1
  [{:keys [props-changed? force-update-part force-update-all] :as env} node]
  (.forEach (aget node "children") (fn [node] (mark-changed-1 env node)))
  (let [me-changed?   (or (props-changed? node env @force-update-part) @force-update-all)]
    (doto node
      (aset "me-changed?" me-changed?)
      (aset "changed?" (or me-changed?
                           (.some (aget node "children") (fn [n] (aget n "changed?"))))))))

(defn- update-parsed-props-
  [{:keys [read tree] :as env}]
  (let [[old-tree tree'] @tree
        node             (first old-tree)]
    (if (:me-changed? node)
      (do
        (vreset! tree [])
        (let [val (parse-props- (merge env { :props parse-props- :old-node node })
                                (node :key) (node :params))]
          (vreset! tree [(rest old-tree) (into tree' @tree)])
          val))
      (if (:changed? node)
        (do
          (vreset! tree [(node :children) []])
          (let [val (read env (node :key) (node :params))
                node  { :value val
                        :children (-> tree deref second)
                        :key (node :key)
                        :params (node :params) }]
            (vreset! tree [(rest old-tree) (conj tree' node)])
            val))
        (do
          (vreset! tree [(rest old-tree) (conj tree' node)])
          (node :value))))))

(defn- update-parsed-props-1-
  [{:keys [read tree] :as env}]
  (let [dtree    @tree
        old-tree (aget dtree 0)
        tree'    (aget dtree 1)
        node     (.shift old-tree)]
    (if (aget node "me-changed?")
      (do
        (vreset! tree #js [])
        (let [val (parse-props-1- (merge env { :props parse-props-1- :old-node node })
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

(defn update-parsed-props
  [state read old-tree changed? force-update-all force-update-part]
  (let [tree (volatile! [[(mark-changed { :props-changed? changed?
                                          :force-update-all force-update-all
                                          :force-update-part force-update-part
                                          :changed-entities (store/changed-entities state)
                                          :state state } old-tree)] []])]
    (vreset! force-update-all false)
    (vreset! force-update-part {})
    (update-parsed-props- { :state state
                            :props update-parsed-props-
                            :read read
                            :tree tree })
    (-> tree deref second first)))

(defn- to-js
  [c]
  (clj->js
   (walk/postwalk (fn [c']
                    (if (set? c')
                      (into {} (map #(vector % true)) c')
                      c')) c)))

(defn update-parsed-props-1
  [state read old-tree changed? force-update-all force-update-part]
  (let [tree (volatile! #js [#js [(mark-changed-1 { :props-changed? changed?
                                                    :force-update-all force-update-all
                                                    :force-update-part force-update-part
                                                    :changed-entities (to-js (store/changed-entities state))
                                                    :state state } old-tree)] #js []])]
    (vreset! force-update-all false)
    (vreset! force-update-part {})
    (update-parsed-props-1- { :state state
                              :props update-parsed-props-1-
                              :read read
                              :tree tree })
    (-> tree deref (aget 1 0))))

(defn force-update!
  [force-update-all force-update-part & [uid]]
  (if uid
    (vswap! force-update-part assoc uid true)
    (vreset! force-update-all true)))
