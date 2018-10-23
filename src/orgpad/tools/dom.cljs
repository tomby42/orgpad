(ns ^{:doc "DOM utils"}
  orgpad.tools.dom)

(def ^:private DEFAULT-WIDTHS (range 0 1025 25))

(defn dom-bb->bb
  [dom-bb]
  [[(.-left dom-bb) (.-top dom-bb)]
   [(.-right dom-bb) (.-bottom dom-bb)]])

(def ^:private tr-rex (js/RegExp "translate\\(([-0-9.]+)px, ([-0-9.]+)px"))
(def ^:private sc-rex (js/RegExp "scale.*"))

(defn- parse-transform
  [el]
  (let [tr (aget el "style" "transform")]
    #js [(.exec tr-rex tr)
         (.exec sc-rex tr)]))

(defn set-translate
  [el x y new-x new-y old-x old-y parent-scale & [ts']]
  (let [ts (or ts' (parse-transform el))]
    (aset el "style" "transform" (str "translate("
                                      (+ x (/ (- new-x old-x) parent-scale)) "px, "
                                      (+ y (/ (- new-y old-y) parent-scale)) "px) "
                                      (aget ts 1)))))

(defn update-translate
  [el new-x new-y old-x old-y parent-scale]
  (let [ts (parse-transform el)
        x (js/parseFloat (aget ts 0 1))
        y (js/parseFloat (aget ts 0 2))]
    (set-translate el x y new-x new-y old-x old-y parent-scale ts)))

(defn update-size
  [el new-x new-y old-x old-y parent-scale]
  (let [w (aget el "style" "width")
        h (aget el "style" "height")
        ww (-> w (.substring 0 (- (.-length w) 2)) js/parseFloat)
        hh (-> h (.substring 0 (- (.-length h) 2)) js/parseFloat)]
    (aset el "style" "width" (str (js/Math.round (+ ww (/ (- new-x old-x) parent-scale))) "px"))
    (aset el "style" "height" (str (js/Math.round (+ hh (/ (- new-y old-y) parent-scale))) "px"))))

(defn update-size-translate-diff
  [el diff-x diff-y parent-scale]
  (let [ts (parse-transform el)
        x (js/parseFloat (aget ts 0 1))
        y (js/parseFloat (aget ts 0 2))
        w (aget el "style" "width")
        h (aget el "style" "height")
        ww (-> w (.substring 0 (- (.-length w) 2)) js/parseFloat)
        hh (-> h (.substring 0 (- (.-length h) 2)) js/parseFloat)]
    (aset el "style" "width" (str (js/Math.round (+ ww (/ (* 2 diff-x) parent-scale))) "px"))
    (aset el "style" "height" (str (js/Math.round (+ hh (/ (* 2 diff-y) parent-scale))) "px"))
    (aset el "style" "transform" (str "translate("
                                      (- x (/ diff-x parent-scale)) "px, "
                                      (- y (/ diff-y parent-scale)) "px) "
                                      (aget ts 1)))))

(defn update-size-translate
  [el new-x new-y old-x old-y parent-scale]
  (update-size-translate-diff el (- new-x old-x) (- new-y old-y) parent-scale))

(defn ffind-tag
  "tag-name is keyword"
  [tag-name]
  (-> tag-name name js/document.getElementsByTagName (aget 0)))

(defn set-el-text
  [el text]
  (aset el "text" text))

(defn create-tmp-div
  [name class]
  (let [el (js/document.createElement "div")]
    (doto el
      (aset "id" name)
      (aset "className" class))
    (js/document.body.appendChild el)
    el))

(defonce root-tmp-el (create-tmp-div "root-tmp-el" "root-tmp"))

(defn dom-bb-size
  [bb]
  [(.-width bb) (.-height bb)])

(defn get-html-size
  [content]
  (when content
    (aset root-tmp-el "innerHTML" content)
    (-> root-tmp-el
        .getBoundingClientRect
        dom-bb-size)))

(defn- gen-wrapped-html
  [{:keys [html width id]}]
  (str "<div style=\"width: " width "px; display: flex;\"><div style=\"overflow-x: visible;\"><div id=\"" id "\" style=\"overflow-y: scroll;\">" html "</div></div></div>\n"))

(defn- gen-wrapped-htmls
  [{:keys [html widths id]}]
  (let [widths (or widths DEFAULT-WIDTHS)]
    (apply str (map-indexed #(gen-wrapped-html {:html html :width %2 :id (str "root-tmp-inner" id "-" %1)}) widths))))

(defn get-element-bbox
  "Get bounding box of HTML element with given id."
  [id]
  (->> id
       (.getElementById js/document)
       .getBoundingClientRect
       dom-bb-size))

(defn- get-wrapped-bb-sizes
  [{:keys [id widths]}]
  (let [widths (or widths DEFAULT-WIDTHS)
        ids (map #(str "root-tmp-inner" id "-" %) (range (count widths)))]
    {:id id
     :bbs (map get-element-bbox ids)}))

;; The input is a vector of maps defined below.
(defn get-multihtml-sizes
  [contents]
  (let [tmp-content (reduce str (map gen-wrapped-htmls contents))]
    (aset root-tmp-el "innerHTML" tmp-content)
    (map get-wrapped-bb-sizes contents)))

;; The input is a map:
;; {:html ... string html representation of the content
;;  :id ... entity id
;;  :widths ... width of the bounding box }
(defn get-html-sizes
  [content]
  (-> [content] get-multihtml-sizes first :bbs))

(defn- compute-size-stats
  [opt-ratio width height]
  (let [ratio (/ width height)
        error (js/Math.abs (- opt-ratio ratio))]
    {:width width
     :height height
     :ratio ratio
     :error error}))

(defn- min-width-fn
  [stats h]
  (apply min (into []
                   (comp
                    (filter #(= h (:height %)))
                    (map :width))
                   stats)))

(defn- delete-nonoptimal-sizes
  [stats]
  (into []
        (comp
         (map #(assoc % :min-width (min-width-fn stats (:height %))))
         (filter #(= (:min-width %) (:width %))))
        stats))

(def ^:private DEFAULT-OPTIMAL-SIZE-PARAMS
  {:opt-ratio 2.0
   :max-width 1000
   :max-height 750
   :min-width 30
   :min-height 30})

; TODO: deal with input parameters for ratio and max width and height
(defn compute-optimal-size
  [sizes]
  (let [{:keys [opt-ratio
                max-width
                max-height]} DEFAULT-OPTIMAL-SIZE-PARAMS
        stats (->> sizes
                   (map #(compute-size-stats opt-ratio (% 0) (% 1)))
                   delete-nonoptimal-sizes)
        optimal-size (apply (partial min-key :error) stats)
        optimal-width (min max-width
                           (max (:width optimal-size)
                                (:min-width DEFAULT-OPTIMAL-SIZE-PARAMS)))
        optimal-height (min max-height
                            (max (:height optimal-size)
                                 (:min-height DEFAULT-OPTIMAL-SIZE-PARAMS)))]
    [optimal-width optimal-height]))
