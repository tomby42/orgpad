(ns ^{:doc "DOM utils"}
  orgpad.tools.dom)

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
  (str "<div style=\"width: " width "px; display: flex;\"><div id=\"" id "\">" html "</div></div>\n"))

(defn- gen-wrapped-htmls
  [{:keys [html widths id]}]
  (reduce str (map #(gen-wrapped-html {:html html :width %1 :id (str "root-tmp-inner" id "-" %2)}) widths (range))))

(defn get-element-bbox
  "Get bounding box of HTML element with given id."
  [id]
  (dom-bb-size (.getBoundingClientRect (.getElementById js/document id))))


(defn- get-wrapped-bb-sizes
  [{:keys [id widths]}]
  (let [ids (map #(str "root-tmp-inner" id "-" %) (range (count widths)))]
    {:id id
     :bbs (map #(get-element-bbox %) ids)}))

;; The input is a vector of maps defined below.
(defn get-multihtml-sizes
  [contents]
  (let [tmp-content (reduce str (map gen-wrapped-htmls contents))]
    (aset root-tmp-el "innerHTML" tmp-content)
    (map #(get-wrapped-bb-sizes %) contents)
    ;(aset root-tmp-el "innerHTML" "")
    ))

;; The input is a map:
;; {:html ... string html representation of the content
;;  :id ... entity id
;;  :widths ... width of the bounding box }
(defn get-html-sizes
  [content]
  (:bbs (first (get-multihtml-sizes [content]))))

(defn- compute-size-stats
  [opt-ratio width height]
  (let [ratio (/ width height)
        abs (fn [v] (if (< v 0) (- v) v))
        error (abs (- opt-ratio ratio))]
    {:width width
     :height height
     :ratio ratio
     :error error}))

(defn- delete-nonoptimal-sizes
  [stats]
  (let [min-width-fn (fn [h] (apply min (map :width (filter #(= h (:height %)) stats))))
        stats' (map #(assoc % :min-width (min-width-fn (:height %))) stats)]
    (filter #(= (:min-width %) (:width %)) stats')))

; TODO: deal with input parameters for ratio and max width and height
(defn compute-optimal-size
  [sizes]
  (let [opt-ratio 2.0
        max-width 1000
        max-height 750
        stats (map #(compute-size-stats opt-ratio (% 0) (% 1)) sizes)
        stats' (delete-nonoptimal-sizes stats)
        optimal-size (apply (partial min-key :error) stats')
        optimal-width (+ (min max-width (max (:width optimal-size) 30)) 10)
        optimal-height (min max-height (max (:height optimal-size) 30))]
    ;(js/console.log stats')
    [optimal-width optimal-height]))
