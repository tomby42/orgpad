(ns ^{:doc "Order numbers"}
  orgpad.tools.order-numbers)

(def ^:private first-char 33)
(def ^:private last-char 127)

(def first-cipher (js/String.fromCharCode first-char))
(def second-cipher (js/String.fromCharCode (inc first-char)))
(def last-cipher (js/String.fromCharCode (dec last-char)))

(def num->str js/String.fromCharCode)
(def mid-cipher (-> first-char (+ last-char) (bit-shift-right 1) num->str))

(def canonical-length 4)

(defn zeros
  [num-digits]
  (let [z (num->str first-char)]
    (apply str (repeat num-digits z))))

(def canonical-zero (zeros canonical-length))

(defn inc-on-pos
  [num pos]
  (loop [i     pos
         done  false
         num'  num]
    (if (or (== i -1)
            done)
      num'
      (let [c (inc (.charCodeAt num' i))]
        (recur (dec i)
               (not= c last-char)
               (str (.substring num' 0 i)
                    (js/String.fromCharCode
                     (if (== c last-char)
                       first-char
                       c))
                    (.substring num' (inc i))))))))

(defn canonical-next
  [num]
  (inc-on-pos num canonical-length))

(defn- extend-by-zeros
  [num n]
  (str num (zeros n)))

(defn number-between
  [num1 num2]
  (let [n1 (count num1)
        n2 (count num2)

        [num1' num2']
        (if (< n1 n2)
          [(extend-by-zeros num1 (- n2 n1)) num2]
          (if (== n1 n2)
            [num1 num2]
            [num1 (extend-by-zeros num2 (- n1 n2))]))

        n (count num1')

        prefix-len (loop [i 0]
                     (if (or (== i n)
                             (not= (- (.charCodeAt num1' i) (.charCodeAt num2' i)) 0))
                       i
                       (recur (inc i))))]
    (if (or (== prefix-len n)
            (== (js/Math.abs
                 (- (.charCodeAt num1' prefix-len)
                    (.charCodeAt num2' prefix-len)))
                1))
      (str (.substring num1' 0 (inc prefix-len))
           mid-cipher)
      
      (str (.substring num1' 0 prefix-len)
           (-> (.charCodeAt num1' prefix-len)
               (+ (.charCodeAt num2' prefix-len))
               (bit-shift-right 1)
               num->str)))))
