(ns linkboard.core
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [linkboard.utils.system :as system-utils]))

(defn- run-system
  [profile]
  (let [config (system-utils/config profile)]
    (log/info "[SYSTEM] System is starting with profile:" profile)
    (ig/load-namespaces config)
    (-> config
      (ig/init)
      (system-utils/at-shutdown))
    (log/info "[SYSTEM] System has been started successfully.")))

(defn -main
  "Run application system in production env."
  []
  (run-system :prod))
