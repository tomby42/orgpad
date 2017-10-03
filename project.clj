(defproject orgpad "0.1.0-SNAPSHOT"
  :description "Orgpad - universal tool for thoughts managing and co-sharing."
  :url "http://www.orgpad.org/"

  :dependencies [[org.clojure/clojure         "1.8.0"]
                 [org.clojure/clojurescript   "1.9.908"]
                 [org.clojure/core.async      "0.3.443"]
                 [org.clojure/test.check      "0.9.0"]
                 [datascript                  "0.16.2"]
                 [com.rpl/specter             "1.0.2"]
                 ;; [rum                         "0.9.1"] ;; local modified copy src/rum 0.10.8
                 [sablono                     "0.7.7"]
                 [com.cemerick/url            "0.1.1"]
                 [cljs-ajax                   "0.6.0"]
                 [cljsjs/react                "15.6.1-0"]
                 [cljsjs/react-dom            "15.6.1-0"]
                 [cljsjs/react-sanfona        "0.0.14-0"]
                 [cljsjs/react-tinymce        "0.5.1-0"]  ;; *
                 [cljsjs/react-tagsinput      "3.13.5-0"] ;; *
                 [cljsjs/react-motion         "0.3.1-0"]  ;; *
                 [cljsjs/react-select         "1.0.0-rc.3"]
                 [cljsjs/latlon-geohash       "1.1.0-0"] ;; *
                 [doo                         "0.1.7"]
                 [org.clojure/data.avl        "0.0.17"]]

  :npm {:dependencies [["@jupyterlab/services" "0.48.0"]
                       ["babel-polyfill" "6.23.0"]]}

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-figwheel "0.5.13"] ;; needs update to lein 2.5.3 at least
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
                             [figwheel-sidecar        "0.5.13"]
                             [binaryage/devtools      "0.9.4"]]
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
                        :externs ["node_modules/@jupyterlab/services/dist/index.js"]
                        :foreign-libs [{:file "node_modules/@jupyterlab/services/dist/index.js"
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
                        :externs ["node_modules/babel-polyfill/dist/polyfill.js", "node_modules/@jupyterlab/services/dist/index.js"]
                        :foreign-libs [{:file "node_modules/babel-polyfill/dist/polyfill.js"
                                        :provides ["babel.polyfill"]},
                                       {:file "node_modules/@jupyterlab/services/dist/index.js"
                                        :provides ["jupyter.services"]}
                                       ]
                        }}

            {:id "prod"
             :source-paths ["src"]
             :compiler {:output-to "resources/public/js/compiled/orgpad.js"
                        :main orgpad.core.boot
                        :optimizations :advanced
                        :pretty-print false
                        :language-in :ecmascript5
                        :closure-warnings {:externs-validation :off :non-standard-jsdoc :off}
                        :externs ["node_modules/@jupyterlab/services/dist/index.js"]
                        :foreign-libs [{:file "node_modules/@jupyterlab/services/dist/index.js"
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
