# ORGPAD

                                         <img width="200px" height="190px" src="notes/orgpad2.svg" />

Universal tool for thoughts managing and co-sharing.

# Dev

* Requirements
 * Java 1.7 and above
 * node 6.x and above
 * phantomjs 1.9x (not tested with 2.x)
 * Leiningen - http://leiningen.org/#install

* Installation
 * clone git repo `git clone git@github.com:tomby42/orgpad.git`
 * run `cd orgpad`
 * run `lein localrepo install -p dev-resources/local-repo/react-tagsinput-3.13.5-0.pom dev-resources/local-repo/react-tagsinput-3.13.5-0.jar cljsjs/react-tagsinput 3.13.5-0`
 * run `lein localrepo install -p dev-resources/local-repo/react-tinymce-0.5.1-0.pom dev-resources/local-repo/react-tinymce-0.5.1-0.jar cljsjs/react-tinymce 0.5.1-0`
 * run `lein deps`
 
* Running dev env
 * run `lein figwheel` and connect from your web browser to `http://localhost:3449`

* Running tests
 * run `lein cljsbuild test`

* File structure
 * `arxiv` - materials related to orgpad development
 * `dev-resources` - local development resuorces for development
 * `doc` - documentation
 * `notes` - various development notes
 * `resuorces`
   * `public`
     * `index.html` - orgpad root html file
   * `test`
     * `test.html` - orgpad root html file for tests
 * `src/orgpad`
     * `components` - react components
     * `core` - core functionality
     * `data` - data structures
     * `dataflow` - dataflow parts
     * `effects` - effects functions
     * `parsers` - read/mutate functions for components
     * `styles` - css styles
     * `tools` - various tools
     * `config.cljs` - configuration for orgpad modules to load
 * `tests` - directory where tests reside
 * `README.md` - this file
 * `project.clj` - project file

## Life cycle
![Life cycle](notes/life-cycle.svg)