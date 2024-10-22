(ns orgpad.components.panel.index.style)

(def style
  (clj->js
   {:tree {:base {:listStyle "none",
                  :backgroundColor "white",
                  :margin 0,
                  :padding 0,
                  :color "black",
                  :fontFamily "lucida grande ,tahoma,verdana,arial,sans-serif",
                  :fontSize "14px"},
           :node {:base {:position "relative"},
                  :link {:cursor "pointer",
                         :position "relative",
                         :padding "0px 5px",
                         :whiteSpace "nowrap",
                         :display "block"},
                  :activeLink {
                               ;; :background "black"
                               ;; :color "white"
                               },
                  :toggle {:base {:position "relative",
                                  :display "inline-block",
                                  :verticalAlign "top",
                                  :marginLeft "-5px",
                                  :height "24px",
                                  :width "24px"},
                           :wrapper {:position "absolute",
                                     :top "50%",
                                     :left "50%",
                                     :margin "-7px 0 0 -7px",
                                     :height "14px"},
                           :height 14,
                           :width 14,
                           :arrow {:fill "black",
                                   :strokeWidth 0}},
                  :header {:base {:display "inline-block",
                                  :verticalAlign "top",
                                  ;; :color "black"
                                  },
                           :active {:background "black"
                                    :color "white"
                                    :padding "2px"},
                           :connector {:width "2px",
                                       :height "12px",
                                       :borderLeft "solid 2px black",
                                       :borderBottom "solid 2px black",
                                       :position "absolute",
                                       :top "0px",
                                       :left "-21px"},
                           :title {:lineHeight "24px",
                                   :verticalAlign "middle"}
                           :icon {:marginRight "3px"}
                           :number {:backgroundColor "black"
                                    :color "white"
                                    :marginLeft "3px"
                                    :padding "2px"
                                    :fontSize "8px"
                                    :width "15px"
                                    :height "15px"
                                    :borderRadius "10px"
                                    :top "-5px"
                                    :position "relative"
                                    :textAlign "center"}
                           :numberPos {}},
                  :subtree {:listStyle "none",
                            :paddingLeft "19px"},
                  :loading {:color "#E2C089"}}}}))
