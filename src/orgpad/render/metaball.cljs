(ns ^{:doc "Blob calculation utilities"} orgpad.render.metaball ) ;blob utilities

;tested
(defn blobFcion [centre, pt, size] ^{:doc "Single blob function"}
      (/ size
         (+ (* (- (first pt) (first centre)) 
               (- (first pt) (first centre)))
            (* (- (last pt) (last centre)) 
               (- (last pt) (last centre)))))
)

;TODO check
(defn massBlobFcion [pt blobs]
  (let [temp (first blobs)]
    (if (first blobs))
        (cons (blobFcion (get temp :centre) pt (get temp :size))
              (massBlobFcion pt (rest blobs)))
        (list))))

;TODO check
(defn resizeBlobs [size blobs]
  (let [temp (first blobs)]
    (if (first blobs))
        (cons (assoc temp :size (* size (get temp :size)))
              (resizeBlobs size (rest blobs)))
        (list))))

;tested
(defn shift [centre1 centre2] ^{:doc "Shift vector of the length cca 20, perpendicular to the line between two points"}
  (let [norm (js/Math.sqrt (+ (* (- (first centre1) (first centre2))
                                 (- (first centre1) (first centre2)))
                              (* (- (last centre1) (last centre2))
                                 (- (last centre1) (last centre2)))))]
    (list (- (/ (* 0 (- (first centre1) (first centre2)))
                norm))
          (/ (* 0 (- (last centre1) (last centre2)))
             norm
    ))
  )
)

;TODO check
(defn getCoef [i j blobs]
  (let [correction (shift (get (nth blobs i) :centre) 
                          (get (nth blobs j) :centre))
        pt (list (+ (/ (+ (first (get (nth blobs i) :centre))
                          (first (get (nth blobs j) :centre)))
                       2)
                    (first correction))
                 (+ (/ (+ (last (get (nth blobs i) :centre))
                          (last (get (nth blobs j) :centre)))
                       2)
                    (last correction)]
    (/ 1
       (reduce + (massBlobFcion pt blobs)))
)

;TODO check
(defn calcBlob [& blobs]
  (let [coef (reduce max (list (for [i (range 0 (- (length blobs) 1))
                                     j (range 0 (- (length blobs) 1))
                                     :when (> i j)]
                                 (getCoef i j blobs)
                                 )))]
    (resizeBlobs (max 1 coef) blobs)))

;
;;bad fcion
;(defn getSizeCoef[blob1 blob2] ^{:doc "Calculates the size coeficient to satisfy the condition of connected blobs"}
;    ( let [correction (shift (get blob1 :centre) (get blob2 :centre))
;           pt (list (+ (/ (+ (first (get blob1 :centre)) 
;                             (first (get blob2 :centre)))
;                          2)
;                       (first correction))
;                    (+ (/ (+ (last (get blob1 :centre)) 
;                             (last (get blob2 :centre)))
;                          2)
;                       (last correction)))]
;              (/ 1 (+ (blobFcion (get blob1 :centre) pt (get blob1 :size))
;                      (blobFcion (get blob2 :centre) pt (get blob2 :size))))
;    )
;)
;
;;bad fcion
;(defn getBlobSize[blobList newBlobList mainBlob] ^{:doc "Return a list of chosen blob and a list of connected blobs with modyfied sizes to create connected blob."}
;  (let [coef (getSizeCoef (peek blobList) mainBlob)
;        mBlob (assoc mainBlob 
;                     :size (* (get mainBlob :size) 
;                              (max coef 1)))  ;does not allow blob to shrink, only to expand
;        mList (concat (list (assoc (peek blobList)
;                                   :size (* (get (peek blobList) :size) 
;                                            (max 1 coef)))) ;does not allow blob to shrink, only to expand
;                      newBlobList)]
;    (if (= 1 (count blobList))
;        (list mBlob mList)
;        (recur (pop blobList) mList mBlob))
;  )
;)

;outdated
(def blobCheck {:size 1, :centre '(70 30)})
(def blob1 {:size 1, :centre '(0 0)})
(def blob2 {:size 1, :centre '(100 100)})
(def blob3 {:size 1, :centre '(150 30)})
(def bL (list blob1 blob2 blob3))

(defn debugTest []
    (do
      (.log js/console "BLOB")
      (let [result (getBlobSize bL '() blobCheck)
            blobList (pop result) 
            mainBlob (peek result)]
        (do
          (.log js/console (count blobList)) 
          (.log js/console (get (nth blobList 0) :size))
          (.log js/console (get (nth blobList 1) :size))
          (.log js/console (get mainBlob :size))
        )
      )
    )
)
