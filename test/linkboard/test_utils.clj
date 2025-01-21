(ns linkboard.test-utils
  (:require [integrant.core :as ig]
            [linkboard.utils.system :as system-util]))

(def ^:dynamic *test-system*
  "Testing system."
  nil)

; TODO: add ability to exclude components from system!
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

(defn get-server-url
  "Return full url from jetty server object."
  [server]
  (let [port (.getLocalPort (first (.getConnectors server)))]
    ; TODO: update with unfied approach!
    ;(str "http://localhost:" port)
    (str "http://host.testcontainers.internal:" port)))
