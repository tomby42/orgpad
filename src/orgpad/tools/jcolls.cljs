(ns ^{:doc "JS collections tools"}
  orgpad.tools.jcolls
  (:require [goog.array]
            [goog.object]))

(defn aset!
  "Sets the value at the index."
  ([array i val]
   (cljs.core/aset array i val))

  ([array idx idx2 & idxv]
   (let [a (or (aget array idx) #js {})]
     (cljs.core/aset array idx a)
     (apply aset! a idx2 idxv))))

(defn aget-safe
  "Returns the value at the index."
  ([array i]
   (if (nil? array)
     nil
     (or (cljs.core/aget array i) #js {})))

  ([array i & idxs]
   (if (nil? array)
     nil
     (let [a (aget array i)]
       (if a
         (apply aget-safe a idxs)
         #js {})))))

(defn aget-nil
  "Returns the value at the index."
  ([array i]
   (or (cljs.core/aget array i) nil))

  ([array i & idxs]
   (let [a (aget array i)]
     (if a
       (apply aget-nil a idxs)
       nil))))

(defn clear!
  [coll]
  (if (js/Array.isArray coll)
    (js/goog.array.clear coll)
    (js/goog.object.clear coll)))
