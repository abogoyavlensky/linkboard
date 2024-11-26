(ns user
  (:require [clojure.tools.namespace.repl :as repl]
            [clojure.repl.deps :as repl-deps]
            [malli.dev :as malli-dev]
            [eftest.runner :as eftest]
            [eftest.report.pretty :as eftest-report]
            [integrant.repl :as ig-repl]
            [integrant.repl.state :as state]
            [linkboard.utils.system :as system-utils]))


(repl/set-refresh-dirs "dev" "src" "test")

; Malli schema instrumentation
(malli-dev/start!)

(defn- dev-config
  [& _]
  (system-utils/config :dev))

(defn reset
  "Restart system."
  []
  (ig-repl/set-prep! dev-config)
  (ig-repl/reset))

(defn stop
  "Stop system."
  []
  (ig-repl/halt))

(defn run-all-tests
  "Run all tests for the project."
  []
  (repl/refresh)
  (eftest/run-tests (eftest/find-tests "test") {:report eftest-report/report}))

(comment
  ; Manage system
  (reset)
  (keys state/system)
  (stop)
  (run-all-tests)

  ; Example of add-lib dynamically:
  (repl-deps/sync-deps)
  (repl-deps/add-lib 'hiccup/hiccup {:mvn/version "2.0.0-RC3"}))
