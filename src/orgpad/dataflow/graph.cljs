(ns ^{:doc "Dataflow graph functionality"}
  orgpad.dataflow.graph
  (:require [clojure.set :as s]
            [orgpad.core.store :as store]
            [orgpad.data.union-find :as uf]))

(def empty-dataflow
  {:nodes {}
   :connections {}
   :components (uf/union-find)})

(defn unit->dataflow-node
  [dataflow unit-id dataflow-info]
  (-> dataflow
      (update-in [:nodes] assoc unit-id dataflow-info)
      (update-in [:components] conj unit-id)))

(def ^:private fconj (fnil conj []))

(defn unit->dataflow-connection
  [dataflow node1 node2 unit-id]
  (-> dataflow
      (update-in [:connections node1 :out] fconj [node2 unit-id])
      (update-in [:connections node2 :in] fconj [node1 unit-id])
      (update-in [:components] uf/union node1 node2)))

(defn- input-nodes
  [dataflow component]
  (into [] (filter #(= (get-in dataflow [:nodes % :node-type]) :orgpad.dataflow/input-node)) component))

(defn- neighbours
  [dataflow node type]
  (set
   (map first
        (get-in dataflow [:connections node type]))))

(defn- pqueue
  [col]
  (reduce conj (.-EMPTY PersistentQueue) col))

(defn topo-order
  [dataflow component]
  (let [inodes (input-nodes dataflow component)]
    (loop [sorted-nodes inodes
           sorted-nodes-set (set inodes)
           queue (pqueue inodes)]
      (if (-> queue peek nil?)
        sorted-nodes
        (let [n (peek queue)
              onei (neighbours dataflow n :out)
              inei (neighbours dataflow n :in)
              queue' (reduce (fn [q nn]
                               (if (contains? sorted-nodes-set nn)
                                 q
                                 (conj q nn)))
                             queue onei)
              no-parents (or (not (empty? (s/difference inei sorted-nodes-set)))
                             (empty? inei)
                             (contains? sorted-nodes-set n))
              sorted-nodes' (if no-parents
                              sorted-nodes
                              (conj sorted-nodes n))
              sorted-nodes-set' (if no-parents
                                  sorted-nodes-set
                                  (conj sorted-nodes-set n))]
          (recur sorted-nodes' sorted-nodes-set' (pop queue')))))))
