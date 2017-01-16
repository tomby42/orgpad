(ns ^{:doc "Geocache for map component"}
  orgpad.tools.geocache
  (:require [orgpad.tools.geohash :as geohash]))

(defn del-from-place!
  [geocache h id]
  (when (aget geocache h)
    (js/delete (aget geocache h id))))

(defn add->place!
  [geocache h id]
  (if (aget geocache h)
    (aset geocache h id true)
    (aset geocache h (js-obj id true))))

(defn create!
  [global-cache id]
  (let [cache-entry (aget global-cache id)]
    (if cache-entry
      (when-not (aget cache-entry "geocache")
        (aset cache-entry "geocache" #js {}))
      (aset global-cache id #js { :geocache #js {} }))))

(defn update-box!
  [global-cache parent-id uid pos size & [old-pos old-size]]
  (let [geocache (aget global-cache parent-id "geocache")
        places (if (and pos size)
                 (geohash/box->hashes (pos 0) (pos 1)
                                      (size 0) (size 1))
                 [])
        old-places (if (and old-pos old-size)
                     (geohash/box->hashes (old-pos 0) (old-pos 1)
                                          (old-size 0) (old-size 1))
                     [])]
    (doseq [h old-places]
      (del-from-place! geocache h uid))
    (doseq [h places]
      (add->place! geocache h uid))))
