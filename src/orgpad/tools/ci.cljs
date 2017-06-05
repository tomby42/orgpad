(ns ^{:doc "CI tools"}
  orgpad.tools.ci
  (:require [cljs.core.async :refer [chan]]))

(defn utterance->intent-regexp
  [descs text]
  (reduce (fn [res desc]
            (let [m (re-seq (:regexp desc) text)]
              (when-not (nil? m)
                (reduced {:parser (:parser desc)
                          :params (zipmap (:params-name desc) (rest m))}))))
          nil descs))

(defn create-regexp-parser
  [utterance->intent-desc]
  (fn [text ctx unit-tree state]
    (let [intent (utterance->intent-regexp utterance->intent-desc text)]
      (if (nil? intent)
        nil
        (let [ch (chan 10)
              _ ((:parser intent) ch (:params intent) {:text text :ctx ctx :unit-tree unit-tree :state state})]
          ch)))))
