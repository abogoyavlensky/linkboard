(ns user
  (:require [clojure.tools.namespace.repl :as repl]
            [clojure.test :as test]
            [integrant.repl :as ig-repl]
            [linkboard.utils.system :as system-utils]))


(repl/set-refresh-dirs "dev" "src" "test")

; TODO: add malli instrumentation!

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

; TODO: try eftest runner

(defn run-all-tests
  "Run all tests for the project."
  []
  (repl/refresh)
  (test/run-all-tests #"linkboard.*-test"))
