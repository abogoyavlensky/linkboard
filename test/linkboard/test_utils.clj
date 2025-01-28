(ns linkboard.test-utils
  (:require [integrant.core :as ig]
            [linkboard.utils.system :as system-util]))

(def ^:dynamic *test-system* nil)

(defn with-system
  "Run the test system before tests."
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
  "Return full url from jetty server object.
  * server - jetty server object
  * env - :host or :container
  :host - localhost
  :container - testcontainers internal host"
  ([server]
   (get-server-url server :host))
  ([server env]
   (let [base-url (case env
                    :host "http://localhost"
                    :container "http://host.testcontainers.internal:")
         port (.getLocalPort (first (.getConnectors server)))]
     (str base-url port))))
