#!/bin/bash

echo "Installing custom packages"
lein localrepo install -p dev-resources/local-repo/react-tagsinput-3.13.5-0.pom dev-resources/local-repo/react-tagsinput-3.13.5-0.jar cljsjs/react-tagsinput 3.13.5-0
lein localrepo install -p dev-resources/local-repo/react-tinymce-0.5.1-0.pom dev-resources/local-repo/react-tinymce-0.5.1-0.jar cljsjs/react-tinymce 0.5.1-0
lein localrepo install -p dev-resources/local-repo/react-motion-0.3.1-0.pom dev-resources/local-repo/react-motion-0.3.1-0.jar cljsjs/react-motion 0.3.1-0
lein localrepo install -p dev-resources/local-repo/latlon-geohash-1.1.0-0.pom dev-resources/local-repo/latlon-geohash-1.1.0-0.jar cljsjs/latlon-geohash 1.1.0-0
