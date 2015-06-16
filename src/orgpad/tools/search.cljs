(ns ^{:doc "Search algorithms"}
  orgpad.tools.search)

(defn binary-search [vec val cmp]
  "Binary search. It will search sorted VEC for VAL. Comparison of
   values will be done by CMP. It returns position of value if exist. It
   returns negative number coding position where to insert the
   value (-(insert position + 1)) if doesn't exist."
  (loop [low 0
         high (dec (count vec))]
    (if (> low high)
      (- (inc low))
      (let [mid (quot (+ low high) 2)
            mid-val (vec mid)]
        (cond (cmp mid-val val) (recur (inc mid) high)
              (cmp val mid-val) (recur low (dec mid))
              :else mid)))))
