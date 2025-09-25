(ns user
  (:require [clojure.tools.namespace.repl :as repl]
            [clojure.repl.deps :as repl-deps]
            [clojure+.error :as error+]
            [malli.dev :as malli-dev]
            [eftest.runner :as eftest]
            [eftest.report.pretty :as eftest-report]
            [integrant.repl :as ig-repl]
            [integrant.repl.state :as state]
            [integrant-extras.core :as ig-extras]))

(repl/set-refresh-dirs "dev" "src" "test")
(malli-dev/start!)
(error+/install!)

(defn reset
  "Restart system."
  []
  (ig-repl/set-prep! #(ig-extras/read-config :dev "config.dev.edn"))
  (ig-repl/reset))

(defn stop
  "Stop system."
  []
  (ig-repl/halt))

(defn run-tests
  "Run all tests for the project."
  ([]
   (run-tests "test"))
  ([param]
   (repl/refresh)
   (eftest/run-tests (eftest/find-tests param) {:report eftest-report/report
                                                :multithread? false})))

(comment
  ; It's convenient to bind shortcuts to these functions in your editor.
  ; Start or restart system
  (reset)
  ; refresh code without restarting system
  (repl/refresh)
  ; Check system state
  (keys state/system)
  ; Stop system
  (stop)
  ; Run all project tests
  (run-tests)
  (run-tests "test/linkboard/home_test.clj")
  (run-tests 'linkboard.search-queries-test/test-preprocess-search-query-integration)

  ; Example of add-lib dynamically
  ; Sync all new libs at once
  (repl-deps/sync-deps)
  ; or sync a specific lib
  (repl-deps/add-lib 'hiccup/hiccup {:mvn/version "2.0.0-RC3"}))
