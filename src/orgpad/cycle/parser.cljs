(ns ^{:doc "Definition of property parser"}
  orgpad.cycle.parser)

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

(defn parse-props
  [state read k params]

  (let [tree (volatile! [])]
    (parse-props- { :state state
                    :props parse-props-
                    :read read
                    :tree tree }
                 k params)
    (-> tree deref first)))

(defn- mark-changed
  [{:keys [props-changed? force-update-part force-update-all] :as env} node]
  (let [mark-changed' (partial mark-changed env)
        node'         (assoc node :children (doall (mapv mark-changed' (node :children))))
        me-changed?   (or (props-changed? node' env @force-update-part) @force-update-all)]

    (-> node'
        (assoc :me-changed? me-changed?)
        (assoc :changed? (or me-changed?
                             (some :changed? (node' :children))
                             false))) ))

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

(defn update-parsed-props
  [state read old-tree changed? force-update-all force-update-part]
  (let [tree (volatile! [[(mark-changed { :props-changed? changed?
                                          :force-update-all force-update-all
                                          :force-update-part force-update-part
                                          :state state } old-tree)] []])]
    (vreset! force-update-all false)
    (vreset! force-update-part {})
    (update-parsed-props- { :state state
                            :props update-parsed-props-
                            :read read
                            :tree tree })
    (-> tree deref second first)))

(defn force-update!
  [force-update-all force-update-part & [uid]]
  (if uid
    (vswap! force-update-part assoc uid true)
    (vreset! force-update-all true)))
