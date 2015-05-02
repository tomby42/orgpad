(set-env!
 :source-paths   #{"src"}
 :resource-paths #{"resources"}
 :dependencies '[
		 [adzerk/boot-cljs      "0.0-2814-3"		:scope "test"]
                 [adzerk/boot-cljs-repl "0.1.9"			:scope "test"]
                 [adzerk/boot-reload    "0.2.6"			:scope "test"]
                 [pandeiro/boot-http    "0.6.3-SNAPSHOT"	:scope "test"]
		 [org.clojure/clojurescript "0.0-2814"		:scope "test"]])

(require
 '[adzerk.boot-cljs      :refer [cljs]]
 '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
 '[adzerk.boot-reload    :refer [reload]]
 '[pandeiro.boot-http    :refer [serve]])

(deftask build []
  (comp (speak)
        (cljs)))

(deftask run []
  (comp (serve)
        (watch)
        (reload)
        (cljs-repl)
        (build)))

(deftask development []
  (task-options! cljs {:optimizations :none
                       :unified-mode true
                       :source-map true}
                 reload {:on-jsload 'orgpad.core.boot/init})
  identity)

(deftask dev
  "Simple alias to run application in development mode"
  []
  (comp (development)
        (run)))
