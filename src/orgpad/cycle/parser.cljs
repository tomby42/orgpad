(ns ^{:doc "Definition of property parser"}
  orgpad.cycle.parser)

(def ^:private force-update (volatile! false))

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
  [{:keys [props-changed?] :as env} node]
  (let [mark-changed' (partial mark-changed env)
        node'         (assoc node :children (doall (mapv mark-changed' (node :children))))
        me-changed?   (or (props-changed? node' env) @force-update)]

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
  [state read old-tree changed?]
  (let [tree (volatile! [[(mark-changed { :props-changed? changed?
                                          :state state } old-tree)] []])]
    (vreset! force-update false)
    (update-parsed-props- { :state state
                            :props update-parsed-props-
                            :read read
                            :tree tree })
    (-> tree deref second first)))

(defn force-update!
  []
  (vreset! force-update true))
