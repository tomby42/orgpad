(ns ^{:doc "Jupyter component"}
  orgpad.components.jupyter.component
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [orgpad.cycle.life :as lc]
            [orgpad.components.registry :as registry]
            [orgpad.tools.rum :as trum]
            [orgpad.tools.orgpad :as ot]
            [orgpad.components.editors.tinymce :as tinymce]))

(def ^:private code-regexp (js/RegExp. "<code>((?:.|[\\r\\n])*?)<\\/code>" "mig"))

(defn- scrape-code
  [text]
  (loop [codes []
         m (.exec code-regexp text)]
    (if (nil? m)
      codes
      (recur (conj codes (aget m 1)) (.exec code-regexp text)))))

(defn- try-exec-code
  [component id view]
  (let [text (:orgpad/jupyter-code view)
        codes (scrape-code text)]
    (lc/transact! component [[:orgpad.jupyter/exec
                              {:id id
                               :view view
                               :codes codes
                               :url (:orgpad/jupyter-url view)}]])))

(defn- render-url-input
  [component id view]
  (println "render url input" id view)
  [:div {:className "react-tagsinput url-editor"}
   [:input {:value (:orgpad/jupyter-url view)
            :placeholder "Jupyter nb server"
            :onChange (fn [e]
                        (lc/transact!
                         component
                         [[:orgpad.jupyter/update
                           {:db/id id
                            :orgpad/view view
                            :key :orgpad/jupyter-url
                            :val (-> e .-target .-value)}]]))}]
   [:div {:className "exec-button" :title "Execute"
          :onClick #(try-exec-code component id view)}
    [:i {:className "fa fa-cogs"}]]])

(defn- render-code-editor
  [component id view]
  (tinymce/tinymce (view :orgpad/jupyter-code)
                   (fn [e]
                     (let [target (aget e "target")
                           unit-tree (first (trum/comp->args component))
                           id (ot/uid unit-tree)
                           view (:view unit-tree)]
                       (println (trum/comp->args component) id unit-tree)
                       (lc/transact!
                        component
                        [[:orgpad.jupyter/update
                          {:db/id id
                           :orgpad/view view
                           :key :orgpad/jupyter-code
                           :val (.call (aget target "getContent") target)}]])))))

(defn- render-results
  [results]
  (mapv (fn [result]
          (mapv (fn [[res-type res-data]]
                  (case res-type
                    "image/png" [:img {:src (str "data:image/png;base64," res-data)}]
                    nil)) result))
        results))

(defn- render-write-mode
  [component {:keys [unit view]} app-state]
  (let [uid (unit :db/id)]
    [:div {:className "jupyter-view"}
     (render-url-input component uid view)
     (render-code-editor component uid view)
     (render-results (:orgpad/jupyter-results view))]))

(defn- render-read-mode
  [component {:keys [view]} app-state]
  [:div {:className "jupyter-view"}
   (render-results (:orgpad/jupyter-results view))])

(rum/defcc jupyter-component < rum/static lc/parser-type-mixin-context
  [component unit-tree app-state]
  (if (= (:mode app-state) :write)
    (render-write-mode component unit-tree app-state)
    (render-read-mode component unit-tree app-state)))

(registry/register-component-info
 :orgpad/jupyter-view
 {:orgpad/default-view-info   {:orgpad/view-type :orgpad/jupyter-view
                               :orgpad/view-name "default"}
  :orgpad/class               jupyter-component
  :orgpad/needs-children-info false
  :orgpad/view-name           "Jupyter"
  :orgpad/view-icon           "far fa-server"})
