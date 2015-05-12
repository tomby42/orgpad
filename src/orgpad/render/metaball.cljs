(ns ^{:doc "Blob calculation utilities"} orgpad.render.metaball ) ;blob utilities

(defn norm-squared [pt1 pt2]
    (+ (* (- (pt1 0) (pt2 0)) 
                  (- (pt1 0) (pt2 0)))
            (* (- (pt1 1) (pt2 1)) 
                       (- (pt1 1) (pt2 1)))))

;tested
(defn blob-fcion [centre, pt, size] "Single blob function."
    (/ size (norm-squared pt centre)))

;tested
(defn total-blob-fcion [pt blobs]
    (let [temp (peek blobs)]
          (if (peek blobs)
                    (conj (total-blob-fcion pt (pop blobs))
                                        (blob-fcion (get temp :centre) pt (get temp :size)))
                            (vector))))

;tested
(defn resize-blobs [size blobs]
    (let [temp (peek blobs)]
          (if (peek blobs)
                    (conj (resize-blobs size (pop blobs))
                                        (assoc temp :size (* size (get temp :size))))
                            (vector))))

;Cljs does not support macros - TODO move to external clj file
;(defmacro transform [expression objects] "A macro applying expression to every element of a vector. Use \"x\" as a target of the expression"
;  (let [x (peek objects)]
;    (if (peek objects)
;        (conj (transform expression (pop objects))
;              expression
;        (vector)))))

;tested - TODO further fine-tuning required, postponed till commiting rendering functions
(defn shift [centre1 centre2] "Shift vector of the length cca 20, perpendicular to the line between two points."
    (let [norm (js/Math.sqrt (norm-squared centre1 centre2))]
          (vector (- (/ (* 20 
                                                (- 
                                                                          (centre1 0) 
                                                                                                 (centre2 0)))
                                          norm))
                              (/ (* 20 
                                                      (- (centre1 1)
                                                                              (centre2 1)))
                                                norm))
            )
    )

;tested
(defn get-coef [i j blobs] "Calculates the minimal size coef. required by blob conectness."
    (let [correction (shift (get (blobs i) :centre) 
                                                      (get (blobs j) :centre))
                  pt (vector (+ (/ (+ (-> (blobs i) :centre first)
                                                                  (-> (blobs j) :centre first))
                                                          2)
                                                    (correction 0))
                                              (+ (/ (+ (-> (blobs i) :centre last)
                                                                                 (-> (blobs j) :centre last))
                                                                           2)
                                                                     (correction 1)))]
          (/ 1
                    (reduce + (total-blob-fcion pt blobs)))))

;tested
(defn calc-blob [& blob-list] "Adjusts the size of given blobs to create a connected metaball."
    (let [blobs (into [] blob-list)
                  coef (reduce max (for [i (range 0 (count blobs))
                                                                        j (range 0 (count blobs))
                                                                                                      :when (> i j)]
                                                                   (get-coef i j blobs)))]
        (do (print coef blobs) 
                (resize-blobs (max 1 coef) blobs)))
      )
