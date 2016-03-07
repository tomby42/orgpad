(ns orgpad.tools.macros)

(defmacro bind
  [fn-name]
  `(-> ~'this ~(symbol (str ".-" fn-name)) (.bind ~'this)))
