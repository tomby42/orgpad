(ns ^{:doc "Circle menu"}
  orgpad.components.menu.circle
  (:require [rum.core :as rum]
            [sablono.core :as html :refer-macros [html]]
            [cljsjs.react-motion]
            [orgpad.tools.math :as math]
            [orgpad.effects.core :as eff]))

(defn- final-child-delta-pos
  [idx {:keys [fly-out-radius base-angle separation-angle child-diam main-diam]}]
  (let [angle (+ base-angle (* idx separation-angle))]
    { :dx (+ (* fly-out-radius (math/cos (math/deg->rads angle))) (/ child-diam 2))
      :dy (+ (* fly-out-radius (math/sin (math/deg->rads angle))) (/ child-diam 2)) } ))

(defn- main-style
  [{:keys [center-x center-y main-diam]}]
   #js { :width main-diam
         :height main-diam
         :top (- center-y (/ main-diam 2))
         :left (+ center-x (/ main-diam 2))
        })

(defn- init-child-style
  [{:keys [main-diam child-diam center-x center-y child-spring-config]}]
  #js { :width child-diam
        :height child-diam
        :top (js/ReactMotion.spring (- center-y (/ (- main-diam child-diam) 2)) child-spring-config)
        :left (js/ReactMotion.spring (+ center-x (/ (+ main-diam child-diam) 2)) child-spring-config)
        :rotate (js/ReactMotion.spring -180 child-spring-config)
        :scale (js/ReactMotion.spring 0.5 child-spring-config)
       })

(defn- final-child-style
  [idx {:keys [child-diam center-x center-y child-spring-config] :as cfg}]
  (let [{:keys [dx dy]} (final-child-delta-pos idx cfg)]
    #js { :width child-diam
          :height child-diam
          :top (js/ReactMotion.spring (- center-y dy) child-spring-config)
          :left (js/ReactMotion.spring (+ center-x dx) child-spring-config)
          :rotate (js/ReactMotion.spring 0 child-spring-config)
          :scale (js/ReactMotion.spring 1 child-spring-config)
         }))

(defn- transform
  [props]
  (str "rotate(" (aget props "rotate") "deg) scale(" (aget props "scale") ")"))

(defn- render-children
  [open? children cfg]
  (let [target-styles (clj->js
                       (map-indexed
                        (fn [idx _]
                          (if open?
                            (final-child-style idx cfg)
                            (init-child-style cfg)))
                        children))
        scale-min (-> cfg init-child-style .-scale .-val)
        scale-max (->> cfg (final-child-style 0) .-scale .-val)

        calculate-styles-for-next-frame
        (fn [prev-styles]
          (let [prev-frame-styles (if open? prev-styles (.reverse prev-styles))
                next-frame-target-styles
                (.map prev-frame-styles
                      (fn [style-in-prev-frame i]
                        (if (== i 0)
                          (aget target-styles i)
                          (let [prev-scale (.-scale (aget prev-frame-styles (dec i)))
                                apply-target-style?
                                (if open?
                                  (>= prev-scale (+ scale-min (:offset cfg)))
                                  (<= prev-scale (- scale-max (:offset cfg))))]
                            (if apply-target-style?
                              (aget target-styles i)
                              style-in-prev-frame) )) ))]
            (if open?
              next-frame-target-styles
              (.reverse next-frame-target-styles)) )) ]
    (js/React.createElement
     js/ReactMotion.StaggeredMotion
     #js { :defaultStyles  target-styles
           :key 0
           :styles calculate-styles-for-next-frame }
     (fn [interpolated-styles]
       (html
        [ :div {}
          (.map interpolated-styles
                (fn [props idx]
                  (html
                   [ :div
                    { :className "circle-menu-child"
                      :key idx
                      :style #js { :left (aget props "left")
                                   :top (aget props "top")
                                   :height (aget props "height")
                                   :width (aget props "width")
                                   :transform (transform props)
                                  }
                     }
                    (get children idx)
                   ])
                 ))
         ])
       ))
    ))

(def ^:private auto-open
  { :did-mount
    (fn [state]
      (when (-> state :rum/args first :init-state)
        ((eff/debounce reset! 100 false) (:rum/local state) true))
      state)
   })

(rum/defcs circle-menu < (rum/local false) auto-open
  [{:keys [rum/react-component rum/local]} {:keys [init-rotate init-scale init-state main-spring-config always-open?] :as config} & children ]
  (let [open? @local
        main-transf (if open?
                      #js { :rotate (js/ReactMotion.spring 0 main-spring-config)
                            :scale (js/ReactMotion.spring 1 main-spring-config) }
                      #js { :rotate (js/ReactMotion.spring init-rotate main-spring-config)
                            :scale (js/ReactMotion.spring init-scale main-spring-config) })]
    [ :div { :className "circle-menu" }
      (render-children open? (subvec children 1) config)
      (js/React.createElement
       js/ReactMotion.Motion
       #js { :style main-transf :key 1 }
       (fn [props]
         (html
          [ :div { :className "circle-menu-main"
                   :style (let [style (main-style config)]
                            (doto style
                              (aset "transform" (transform props)) ))
                   :onClick (fn [] (when-not (and always-open? @local) (swap! local not))) }
           (first children) ] ) ) ) ]
    ))

(comment
( mc/circle-menu { :always-open? true
                                       :init-state false
                                       :init-rotate -135
                                       :init-scale 0.5
                                       :main-spring-config #js [500 30]
                                       :fly-out-radius 130
                                       :base-angle 90
                                       :separation-angle 40
                                       :child-diam 48
                                       :center-x 300
                                       :center-y 300
                                       :main-diam 90
                                       :offset 0.2
                                       :child-spring-config #js [400 28] }
                     [ :i {:className "fa fa-close fa-3x"} ]
                     [ :i {:className "fa fa-pencil fa-lg"} ]
                     [ :i {:className "fa fa-at fa-lg"} ]
                     [ :i {:className "fa fa-camera fa-lg"} ] ) 
)
