(ns linkboard.process
  (:require [clojure.java.process :as process]
            [clojure.tools.logging :as log]
            [integrant-extras.core :as ig-extras]
            [integrant.core :as ig]))

(defmethod ig/assert-key ::process
  [_ params]
  (ig-extras/validate-schema!
    {:component ::process
     :data params
     :schema [:map
              [:cmd [:vector {:min 1} string?]]]}))

(defmethod ig/init-key ::process
  [_ options]
  (log/info (format "[DB] Starting process %s..." (:cmd options)))
  {:options options
   ; TODO: uncomment :err to write process output to server log
   :process (apply process/start {; :err :inherit
                                  :out :inherit} (:cmd options))})

(defmethod ig/halt-key! ::process
  [_ {:keys [options process]}]
  (log/info (format "[DB] Stopping process %s..." (:cmd options)))
  (when (some? process)
    (.destroyForcibly process)))
