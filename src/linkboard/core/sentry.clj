(ns linkboard.core.sentry
  (:require [clojure.tools.logging :as log]
            [integrant-extras.core :as ig-extras]
            [integrant.core :as ig]
            [sentry-clj.core :as sentry]))

(defn- set-default-exception-handler!
  "Set a default uncaught exception handler that reports to Sentry."
  []
  (Thread/setDefaultUncaughtExceptionHandler
    (fn [thread ex]
      (log/error ex "Uncaught exception on" (.getName thread)))))

(defmethod ig/assert-key ::sentry
  [_ params]
  (ig-extras/validate-schema!
    {:component ::sentry
     :data params
     :schema [:map
              [:dsn any?]]}))

(defmethod ig/init-key ::sentry
  [_ {:keys [dsn]}]
  (if dsn
    (do
      (log/info "[SENTRY] Initialising Sentry...")
      (sentry/init! dsn {:traces-sample-rate 1.0
                         :logs-enabled true})
      (log/info "[SENTRY] Sentry initialised successfully.")
      (set-default-exception-handler!)
      :sentry-initialized)
    (log/info "[SENTRY] No Sentry DSN provided.")))

(defmethod ig/halt-key! ::sentry
  [_ status]
  (log/info "[DB] Closing Sentry SDK...")
  (when (= status :sentry-initialized)
    (sentry/close!)))
