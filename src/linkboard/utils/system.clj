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

(def ^:private SYSTEM-CONFIG-PATH "system.edn")

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
  [profile]
  {:pre [(contains? #{:dev :test :prod} profile)]}
  (-> SYSTEM-CONFIG-PATH
    (io/resource)
    (aero/read-config {:profile profile})))

(defn validate-schema!
  "Validate data against schema and throw a humanized error if data is not valid."
  [{:keys [schema data error-message]}]
  (some-> schema
    (mu/closed-schema)
    (m/explain data)
    (me/with-spell-checking)
    (me/humanize)
    (#(throw (Exception. (str error-message ": " %))))))

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
