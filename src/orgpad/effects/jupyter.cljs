(ns ^{:doc "Definition of jupyter services"}
  orgpad.effects.jupyter
  (:require [jupyter.services]))

(def ^:private running-kernels (volatile! {}))

(def ^:private services (aget js/window "jupyter-js-services"))
(def ^:private service-kernel (aget services "Kernel"))

(defn- start-kernel
  [url]
  (let [connection-info #js {:baseUrl url
                             :ws (-> url (.split ":") (.slice 1) (.join ":"))}]
    (-> (.getSpecs service-kernel connection-info)
        (.then (fn [kernel-specs]
                 (aset connection-info "name" (aget kernel-specs "default"))
                 (.startNew service-kernel connection-info)))
        (.then (fn [kernel]
                 (js/console.log "staring jupyter kernel" kernel)
                 (vswap! running-kernels assoc url kernel)
                 kernel)))))

(defn- kernel-exec
  [kernel code cb]
  (js/console.log "executing code" kernel code)
  (let [f (.execute kernel #js {:code code})]
    (doto f
      (aset "onDone" cb)
      (aset "onReply" cb)
      (aset "onIOPub" cb))))

(defn exec-code
  [url code cb]
  (let [kernel (get @running-kernels url nil)]
    (if (nil? kernel)
      (-> (start-kernel url)
          (.then (fn [kernel]
                   (kernel-exec kernel code cb))))
      (kernel-exec kernel code cb))))
