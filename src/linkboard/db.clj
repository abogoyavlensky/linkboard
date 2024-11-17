(ns linkboard.db
  (:require [clojure.tools.logging :as log]
            [hikari-cp.core :as cp]
            [integrant.core :as ig]
            ; Import for converting timestamp fields
            [next.jdbc.date-time]
            [ragtime.next-jdbc :as ragtime-jdbc]
            [ragtime.repl :as ragtime-repl]
            [linkboard.utils.system :as system-utils]))

(defmethod ig/assert-key ::db
  [_ params]
  (system-utils/validate-schema!
    {:data params
     :schema [:map
              [:jdbc-url string?]]
     :error-message (format "Invalid %s component config" ::db)}))

(defmethod ig/init-key ::db
  [_ options]
  (log/info (str "[DB] Starting database connection pool..."))
  (let [datasource (cp/make-datasource options)]
    (ragtime-repl/migrate
      {:datastore (ragtime-jdbc/sql-database datasource)
       :migrations (ragtime-jdbc/load-resources "migrations")})
    datasource))


(defmethod ig/halt-key! ::db
  [_ datasource]
  (log/info (str "[DB] Closing database connection pool..."))
  (cp/close-datasource datasource))
