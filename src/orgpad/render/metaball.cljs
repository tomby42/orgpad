(ns ^{:doc "Metaball calculations - metaball fn"} orgpad.render.metaball
  (:require [orgpad.render.graph]))

(defn blob-fn [node pt] "Single blob function."
  (let [center (get node :center)
        size (get node :size)]
    (if (= center pt)
      1
      (/ size
         (/ (orgpad.render.graph/norm pt center) 0.90)))))

(defn total-blob-fn [nodes pt] "Calculate total blob function from given nodes in given point."
  (reduce + (map #(blob-fn % pt) nodes)))

(defn- vertex? [node edge]
  (or (= (get node :center)
         (-> edge (get :nodes) first (get :center)))
      (= (get node :center)
         (-> edge (get :nodes) second (get :center)))))

(defn- filter-nodes [nodes edge] "Separate nodes into two sets - the ones on the edge and the others."
  (let [off-edge (filter #(not (vertex? % edge))
                         nodes)
        on-edge (filter #(vertex? % edge)
                          nodes)]
    (vector off-edge on-edge)))

(defn connect-nodes 
  ([nodes node1 node2] "Fit the size of given nodes to connect them by blob-fn."
   (let [center1 (get node1 :center)
         center2 (get node2 :center)
         midpoint (vector (/ (+ (first center1)
                                (first center2))
                             2)
                          (/ (+ (second center1)
                                (second center2))
                             2))
         total (total-blob-fn nodes
                              midpoint)]
     (vector (update-in node1 [:size] / (min 1 total))
             (update-in node2 [:size] / (min 1 total)))))
  ([node1 node2] "Connect two nodes by blob-fn."
   (connect-nodes (vector
                    node1
                    node2)
                  node1 
                  node2)))

(defn connect-all-nodes "Connect given nodes into blob."
  ([nodes]
   (connect-all-nodes nodes (orgpad.render.graph/spanning-tree nodes)))
  ([nodes edges]
   (if (> (count edges) 0)
     (let [edge (peek edges)
           separated-nodes (filter-nodes nodes edge)
           on-edge (peek separated-nodes)
           mod-nodes (connect-nodes nodes
                                    (first on-edge)
                                    (last on-edge))
           new-nodes (into []
                           (concat (first separated-nodes) mod-nodes))]
       (recur new-nodes (pop edges)))
     nodes)))

