(ns ^{:doc "File input componet"}
  orgpad.components.input.file
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.tools.rum :as trum]))

(defn- promise-fn
  [type file resolve reject]
  (let [reader (js/FileReader.)]
    (set! (.-onload reader)
          (fn [e]
            (resolve (aget e "target" "result"))))
    (condp = type
      "binary" (.readAsBinaryString reader file)
      "buffer" (.readAsArrayBuffer reader file)
      "text"   (.readAsText reader file)
      "url"    (.readAsDataURL reader file))))

(defn- load-files
  [ev on-change type]
  (let [files (aclone (-> ev .-target .-files))]
    (->
     (js/Promise.all (.map files (fn [file] (js/Promise. (partial promise-fn type file)))))
     (.then
      (fn [zip-result]
        (on-change (vec zip-result)))))))

(rum/defcs file-input < rum/static
  [state {:keys [class-name type on-change]} & children]

  [ :div { :className class-name
           :onClick (fn [ev]
                      (-> state (trum/ref-node :fileInput) .click)) }

   [ :input.hidden-input-style { :type "file"
                                 :ref "fileInput"
                                 :style { :position "absolute",
                                          :top "-9999px" }
                                 :name "files[]"
                                 :onChange #(load-files % on-change (or type "text")) } ]
   children ])
