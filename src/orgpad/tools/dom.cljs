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
    (js/console.log "get-html-size: " content)
    (aset root-tmp-el "innerHTML" content)
    (js/console.log "Bounding box: " (-> root-tmp-el
        .getBoundingClientRect
        dom-bb-size))
    (-> root-tmp-el
        .getBoundingClientRect
        dom-bb-size)))

(defn get-html-size-bounded
  [content width]
  (js/console.log "get-html-size-bounded: " content)
  (let [content' (str "<div style=\"width: " width "px; display: flex;\"><div id=\"root-tmp-inner\">" content "</div></div>")]
  ;(let [content' (html [:div {:style {:width width :height height}
  ;                            :dangerouslySetInnerHTML {:__html content}}])]
    (when content'
      (aset root-tmp-el "innerHTML" content')
      (js/console.log "Bounding box: " (-> root-tmp-el
          .getBoundingClientRect
          dom-bb-size))
      (js/console.log "Child bounding box: "
        (dom-bb-size
          (.getBoundingClientRect (.getElementById js/document "root-tmp-inner"))))
      (-> root-tmp-el
          .getBoundingClientRect
          dom-bb-size))))

(defn- gen-wrapped-html
  [{:keys [html width id]}]
  (str "<div style=\"width: " width "px; display: flex;\"><div id=\"" id "\" style=\"flex-grow: 0\">" html "</div></div>"))

;; Input contents is a vector of the following maps:
;; {:html ... string html representation of the content
;;  :width ... width of the bounding box }
(defn get-html-sizes
  [contents]
  (let [contents' (map #(assoc %1 :id (str "root-tmp-inner" %2)) contents (range))
        content (reduce str (map gen-wrapped-html contents'))]
    (aset root-tmp-el "innerHTML" content)
    (map #(dom-bb-size (.getBoundingClientRect (.getElementById js/document (:id %)))) contents')))
