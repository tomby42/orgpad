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

(defn update-translate
  [el new-x new-y old-x old-y parent-scale]
  (let [ts (parse-transform el)
        x (js/parseInt (aget ts 0 1))
        y (js/parseInt (aget ts 0 2))]
    (aset el "style" "transform" (str "translate("
                                      (+ x (/ (- new-x old-x) parent-scale)) "px, "
                                      (+ y (/ (- new-y old-y) parent-scale)) "px) "
                                      (aget ts 1)))))
