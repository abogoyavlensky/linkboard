(ns linkboard.process
  (:require [clojure.java.process :as process]
            [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [linkboard.utils.system :as system-utils]))

(defmethod ig/assert-key ::process
  [_ params]
  (system-utils/validate-schema!
    {:data params
     :schema [:map
              [:cmd [:vector {:min 1} string?]]]
     :error-message (format "Invalid %s component config" ::process)}))

(defmethod ig/init-key ::process
  [_ options]
  (log/info (format "[DB] Starting process %s..." (:cmd options)))
  {:options options
   :process (apply process/start {:out :inherit
                                  :err :inherit} (:cmd options))})

(defmethod ig/halt-key! ::process
  [_ {:keys [options process]}]
  (log/info (format "[DB] Stopping process %s..." (:cmd options)))
  (when (some? process)
    (.destroyForcibly process)))
