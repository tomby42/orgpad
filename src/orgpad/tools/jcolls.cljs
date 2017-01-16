(ns ^{:doc "JS collections tools"}
  orgpad.tools.jcolls)

(defn aset!
  "Sets the value at the index."
  ([array i val]
   (cljs.core/aset array i val))
  
  ([array idx idx2 & idxv]
   (let [a (or (aget array idx) #js {})]
     (cljs.core/aset array idx a)
     (apply aset a idx2 idxv))))
