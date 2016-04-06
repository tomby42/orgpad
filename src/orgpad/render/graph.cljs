(ns ^{:doc "Graph algorithms - norm, spanning-tree"} orgpad.render.graph
  (:require [clojure.set]))

(defn- generate-edges [nodes] "Generate complete graph edges for given nodes."
  (remove nil?
          (for [x (range (count nodes))
                y (range (count nodes))]
            (if (< x y)
              [(nth nodes x) (nth nodes y)]))))

(defn norm [pt1 pt2] "Euclidian norm"
  (Math/sqrt
    (+ (* (- (pt1 0)
             (pt2 0))
          (- (pt1 0)
             (pt2 0)))
       (* (- (pt1 1)
             (pt2 1))
          (- (pt1 1)
             (pt2 1))))))

(defn- rate-edges [nodes] "Rate the edges by their length."
  (let [edges (generate-edges nodes)
        rated-edges (for [edge edges]
                      {:weight (norm (get (edge 0) :center)
                                     (get (edge 1) :center))
                       :nodes [(edge 0) (edge 1)]})]
    (->> rated-edges
         (sort-by :weight)
         (into []))))

(defn- create-basic-components [nodes] "Turn list of nodes into list of one-point sets."
  (into [] (map (comp set list) nodes)))

(defn- component-merge [component a b] "Merge two sets of edges."
  (conj (filter #(not (or (contains? % a)
                          (contains? % b)))
                component)
        (apply clojure.set/union
               (concat (filter #(contains? % a) component)
                       (filter #(contains? % b) component)))))

(defn- add-edge [[component tree] edge] "Check if adding the edge does not create a cycle - if not, add it the proto-spanning-tree"
  (let [tmp (component-merge component
                             (-> edge (get :nodes) first)
                             (-> edge (get :nodes) second))]
    (if (< (count tmp) (count component))
      [tmp (cons edge tree)]
      [tmp tree])))

(defn spanning-tree [nodes] "Spanning-tree - Jarnik's alg."
  (let [rated-edges (rate-edges nodes)]
    (->> rated-edges
         (reduce add-edge
                 [(create-basic-components nodes) (vector)])
         second
         (into []))))
