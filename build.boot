(set-env!
 :source-paths   #{"src"}
 :resource-paths #{"resources"}
 :dependencies '[[org.clojure/clojure   "1.7.0"                 :scope "test"]
		 [adzerk/boot-cljs      "1.7.48-2"		:scope "test"]
                 [adzerk/boot-cljs-repl "0.1.10-SNAPSHOT"	:scope "test"]
                 [adzerk/boot-reload    "0.3.1"			:scope "test"]
                 [pandeiro/boot-http    "0.6.3-SNAPSHOT"	:scope "test"]
		 [org.clojure/clojurescript "1.7.48"		:scope "test"]
		 [org.clojure/test.check "0.7.0"		:scope "test"]
		 [cider/cider-nrepl      "0.9.1"		:scope "test"]
		 [com.wagjo/cljs-diff    "0.1.0-SNAPSHOT"	:scope "test"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha" :scope "test"]
                 [datascript             "0.11.6"               :Scope "test"]])

(require
 '[adzerk.boot-cljs      :refer [cljs]]
 '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
 '[adzerk.boot-reload    :refer [reload]]
 '[pandeiro.boot-http    :refer [serve]]
 '[clojure.java.shell    :as shell]
 '[boot.repl		 :as brepl])

(swap! boot.repl/*default-dependencies*
       concat '[[cider/cider-nrepl "0.9.1"]])

(swap! boot.repl/*default-middleware*
       conj 'cider.nrepl/cider-middleware)

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

(deftask run-phantom
  "Runs phantomjs tests"
  []
  (defn middleware [next-handler]
    (defn handler [fileset]
      (let [new-fileset (next-handler fileset)
            res (shell/sh "phantomjs" "target/unit-test.js" "target/unit-test.html")]
        (println (:out res))
        (println (:err res))
        new-fileset))))

(deftask testme
  "Test the app"
  []
  (set-env! :source-paths   #{"src" "test"})
  (set-env! :resource-paths #{"resources" "phantom"})
  (comp (run-phantom) (build)))
