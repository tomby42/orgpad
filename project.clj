(defproject orgpad "0.1.0-SNAPSHOT"
  :description "Orgpad - universal tool for thoughts managing and co-sharing."
  :url "http://www.orgpad.org/"

  :dependencies [[org.clojure/clojure         "1.8.0"]
                 [org.clojure/clojurescript   "1.9.494"]
                 [org.clojure/core.async      "0.3.442"]
                 [org.clojure/test.check      "0.9.0"]
                 [datascript                  "0.16.0"]
                 [com.rpl/specter             "0.10.0"]
                 [rum                         "0.9.1"]
                 [com.cemerick/url            "0.1.1"]
                 [cljs-ajax                   "0.5.8"]
                 [cljsjs/react                "15.4.0-0"]
                 [cljsjs/react-dom            "15.4.0-0"]
                 [cljsjs/react-sanfona        "0.0.14-0"]
                 [cljsjs/react-tinymce        "0.5.1-0"]  ;; *
                 [cljsjs/react-tagsinput      "3.13.5-0"] ;; *
                 [cljsjs/react-motion         "0.3.1-0"]  ;; *
                 [cljsjs/react-select         "1.0.0-rc.1"]
                 [cljsjs/latlon-geohash       "1.1.0-0"] ;; *
                 [doo                         "0.1.7"]
                 [org.clojure/data.avl        "0.0.17"]]

  :npm {:dependencies [["jupyter-js-services" "0.21.1"]]}

  :plugins [[lein-cljsbuild "1.1.5"]
            [lein-figwheel "0.5.10"] ;; needs update to lein 2.5.3 at least
            [lein-less "1.7.5"]
            [lein-localrepo "0.5.3"]
            [lein-doo "0.1.7"]
            [lein-npm "0.6.2"]]

  :hooks [leiningen.less leiningen.cljsbuild]

  :source-paths ["src"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target" "resources/test/js/compiled"]

  :less {:source-paths ["src/orgpad/styles"]
         :target-path "resources/public/css"}

  :profiles {:dev
             {:dependencies [[com.cemerick/piggieback "0.2.1"]
                             [org.clojure/tools.nrepl "0.2.12"]
                             [figwheel-sidecar        "0.5.10"]]
              }

             :repl {:plugins [[cider/cider-nrepl "0.11.0-SNAPSHOT"]] }}

  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

  :cljsbuild
  {
   :builds [{:id "dev"

             :source-paths ["src" "sandbox"]
             :figwheel {:websocket-host "localhost"
                        :on-jsload "orgpad.core.boot/on-js-reload" }

             :compiler {:main orgpad.core.boot
                        :asset-path "js/compiled/out"
                        :output-to "resources/public/js/compiled/orgpad.js"
                        :output-dir "resources/public/js/compiled/out"
                        :source-map-timestamp true
                        :language-in :ecmascript5
                        :externs ["node_modules/jupyter-js-services/dist/index.js"]
                        :foreign-libs [{:file "node_modules/jupyter-js-services/dist/index.js"
                                        :provides ["jupyter.services"]}]

                        }}

            {:id "test"
             :source-paths ["src" "test"]
             :compiler {:main orgpad.test
                        :output-to "resources/test/js/compiled/orgpad.js"
                        :output-dir "resources/test/js/compiled/out"
                        :asset-path "js/compiled/out"
                        :optimizations :whitespace
                        :pretty-print true
                        :language-in :ecmascript5
                        :externs ["node_modules/jupyter-js-services/dist/index.js"]
                        :foreign-libs [{:file "node_modules/jupyter-js-services/dist/index.js"
                                        :provides ["jupyter.services"]}]
                        }}

            {:id "prod"
             :source-paths ["src"]
             :compiler {:output-to "resources/public/js/compiled/orgpad.js"
                        :main orgpad.core.boot
                        :optimizations :advanced
                        :pretty-print false
                        :language-in :ecmascript5
                        :externs ["node_modules/jupyter-js-services/dist/index.js"]
                        :foreign-libs [{:file "node_modules/jupyter-js-services/dist/index.js"
                                        :provides ["jupyter.services"]}]
                        }}]

   :test-commands {"test" ["phantomjs"
                           "resources/test/test.js"
                           "resources/test/test.html"]}

   }

  :figwheel {
             ;; :http-server-root "public" ;; default and assumes "resources"
             ;; :server-port 3449 ;; default
             ;; :server-ip "127.0.0.1"

             :css-dirs ["resources/public/css"] ;; watch and update CSS

             ;; Start an nREPL server into the running figwheel process
             ;; :nrepl-port 7888

             ;; Server Ring Handler (optional)
             ;; if you want to embed a ring handler into the figwheel http-kit
             ;; server, this is for simple ring servers, if this
             ;; doesn't work for you just run your own server :)
             ;; :ring-handler hello_world.server/handler

             ;; To be able to open files in your editor from the heads up display
             ;; you will need to put a script on your path.
             ;; that script will have to take a file path and a line number
             ;; ie. in  ~/bin/myfile-opener
             ;; #! /bin/sh
             ;; emacsclient -n +$2 $1
             ;;
             ;; :open-file-command "myfile-opener"

             ;; if you want to disable the REPL
             ;; :repl false

             ;; to configure a different figwheel logfile path
             ;; :server-logfile "tmp/logs/figwheel-logfile.log"
             })
