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
