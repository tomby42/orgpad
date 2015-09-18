(defproject orgpad "0.1.0-SNAPSHOT"
  :description "Orgpad - universal tool for thoughts managing and co-sharing."
  :url "http://www.orgpad.org/"

  :dependencies [[org.clojure/clojure         "1.7.0"]
                 [org.clojure/clojurescript   "1.7.122"]
                 [org.clojure/core.async      "0.1.346.0-17112a-alpha"]
                 [org.clojure/test.check      "0.8.2"]
                 [com.wagjo/cljs-diff         "0.1.0-SNAPSHOT"]
                 [datascript                  "0.12.1"]
                 [rum                         "0.3.0"]
                 ]

  :plugins [[lein-cljsbuild "1.0.6"]
            [lein-figwheel "0.3.9-SNAPSHOT"]]

  :source-paths ["src"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target" "resources/test/js/compiled"]

  :cljsbuild 
  {
   :builds [{:id "dev"
             :source-paths ["src"]
             
             :figwheel { :on-jsload "orgpad.core.boot/on-js-reload" }

             :compiler {:main orgpad.core.boot
                        :asset-path "js/compiled/out"
                        :output-to "resources/public/js/compiled/orgpad.js"
                        :output-dir "resources/public/js/compiled/out"
                        :source-map-timestamp true }}
            
            {:id "test" 
             :source-paths ["src" "test"]
             :compiler {:main orgpad.test
                        :output-to "resources/test/js/compiled/orgpad.js"
                        :output-dir "resources/test/js/compiled/out"
                        :asset-path "js/compiled/out"
                        :optimizations :whitespace
                        :pretty-print true}}

            {:id "min"
             :source-paths ["src"]
             :compiler {:output-to "resources/public/js/compiled/orgpad.js"
                        :main orgpad.core.boot
                        :optimizations :advanced
                        :pretty-print false}}]

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
