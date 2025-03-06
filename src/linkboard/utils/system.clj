(ns linkboard.utils.system
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [malli.core :as m]
            [malli.error :as me]
            [malli.util :as mu])
  (:import (clojure.lang IFn)
           (java.net ServerSocket)))

(def ^:private SYSTEM-CONFIG-PATH "config.edn")

; Add #ig/ref tag for reading integrant config from aero.
(defmethod aero/reader 'ig/ref
  [_ _ value]
  (ig/ref value))

(defmethod aero/reader 'free-port
  [_ _ _value]
  (with-open [socket (ServerSocket. 0)]
    (.getLocalPort socket)))

(defn config
  "Return edn config with all variables set."
  ([profile]
   (config profile nil))
  ([profile config-path]
   {:pre [(contains? #{:dev :test :prod} profile)]}
   (-> (or config-path SYSTEM-CONFIG-PATH)
     (io/resource)
     (aero/read-config {:profile profile
                        :resolver aero/resource-resolver}))))

(defn validate-schema!
  "Validate data against schema and throw a humanized error if data is not valid."
  [{:keys [component schema data]}]
  (some-> schema
    (mu/closed-schema)
    (m/explain data)
    (me/with-spell-checking)
    (me/humanize)
    (#(throw (Exception. (format "Invalid %s component config: %s" component %))))))

(defn at-shutdown
  "Add hook for shutdown system on sigterm."
  [system]
  (-> (Runtime/getRuntime)
    (.addShutdownHook
      (Thread. ^IFn (bound-fn []
                      (log/info "[SYSTEM] System is stopping...")
                      (ig/halt! system)
                      (shutdown-agents)
                      (log/info "[SYSTEM] System has been stopped."))))))
