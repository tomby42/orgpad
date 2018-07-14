(ns orgpad.components.input.slider
  (:require [orgpad.tools.js-events :as jev]
            [orgpad.tools.math :refer [normalize-range]]))

(defn render-slider
  [{:keys [min max value on-change]}]
  (let [on-change' (fn [ev]
                     (on-change (normalize-range 0 max (-> ev .-target .-value))))]
  [:div.slider
   [:input {:type "range" :min min :max max :step 1 :value value
            :onMouseDown jev/stop-propagation
            :onMouseUp jev/stop-propagation
            :onMouseMove jev/stop-propagation
            :onBlur jev/stop-propagation
            :onChange on-change' } ]
     [:input.val {:type "text" :value value
                  :onBlur jev/stop-propagation
                  :onMouseDown jev/stop-propagation
                  :onChange on-change'}]]))
