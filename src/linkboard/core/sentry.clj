(ns linkboard.core.sentry
  (:require [clojure.tools.logging :as log]
            [integrant-extras.core :as ig-extras]
            [integrant.core :as ig]
            [sentry-clj.core :as sentry])
  (:import [io.sentry Sentry]))

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

(defn init-sentry! [dsn]
  (Sentry/init
    (reify io.sentry.Sentry$OptionsConfiguration
      (configure [_ options]
        (.setDsn options dsn)
        ;; Enable tracing
        (.setTracesSampleRate options 1.0)
        ;; Enable structured logs
        (-> options .getLogs (.setEnabled true))))))

(defmethod ig/init-key ::sentry
  [_ {:keys [dsn]}]
  (if dsn
    (do
      (log/info "[SENTRY] Initialising Sentry...")

      ; TODO: use sentry-clj wrapper instead
      ;(sentry/init! dsn {:traces-sample-rate 1.0})
      (init-sentry! dsn)

      (log/info "[SENTRY] Sentry initialised successfully.")
      (set-default-exception-handler!)
      :sentry-initialized)
    (log/info "[SENTRY] No Sentry DSN provided.")))

(defmethod ig/halt-key! ::sentry
  [_ status]
  (log/info "[DB] Closing Sentry SDK...")
  (when (= status :sentry-initialized)
    (sentry/close!)))
