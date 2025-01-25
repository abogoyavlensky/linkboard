(ns linkboard.test-utils
  (:require [integrant.core :as ig]
            [linkboard.utils.system :as system-util]))

(def ^:dynamic *test-system* nil)

; TODO: add ability to exclude components from system!
(defn with-system
  "Run the system before tests."
  ([]
   (with-system nil))
  ([config-path]
   (fn
     [test-fn]
     (let [test-config (system-util/config :test config-path)]
       (ig/load-namespaces test-config)
       (binding [*test-system* (ig/init test-config)]
         (try
           (test-fn)
           (finally
             (ig/halt! *test-system*))))))))

(defn get-server-url
  "Return full url from jetty server object."
  [server]
  (let [port (.getLocalPort (first (.getConnectors server)))]
    ; TODO: update with unfied approach!
    (str "http://localhost:" port)))

(defn get-server-url-inside-testcontainer
  [server]
  (let [port (.getLocalPort (first (.getConnectors server)))]
    (str "http://host.testcontainers.internal:" port)))
