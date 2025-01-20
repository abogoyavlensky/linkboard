(ns linkboard.test-utils
  (:require [integrant.core :as ig]
            [linkboard.utils.system :as system-util]))

(def ^:dynamic *test-system*
  "Testing system."
  nil)

(defn with-system
  "Run the whole system before tests."
  [test-fn]
  (let [test-config (system-util/config :test)]
    (ig/load-namespaces test-config)
    (binding [*test-system* (ig/init test-config)]
      (try
        (test-fn)
        (finally
          (ig/halt! *test-system*))))))
