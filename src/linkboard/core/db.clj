(ns linkboard.core.db
  (:require [clojure.tools.logging :as log]
            [hikari-cp.core :as cp]
            [honey.sql :as honey]
            [integrant-extras.core :as ig-extras]
            [integrant.core :as ig]
            [next.jdbc :as jdbc]
            ; Import for converting timestamp fields
            [next.jdbc.date-time]
            [next.jdbc.result-set :as jdbc-rs]
            [ragtime.next-jdbc :as ragtime-jdbc]
            [ragtime.repl :as ragtime-repl]
            [sentry-clj.tracing :as tracing]))

; Common functions

(defn exec!
  "Send query to db and return vector of result items."
  [db query]
  (let [query-sql (honey/format query)]
    (tracing/with-start-child-span "db.sql.query" (first query-sql)
      (jdbc/execute! db query-sql {:builder-fn jdbc-rs/as-unqualified-kebab-maps
                                   :return-keys [:id]}))))

(defn exec-one!
  "Send query to db and return single result item."
  [db query]
  (let [query-sql (honey/format query)]
    (tracing/with-start-child-span "db.sql.query" (first query-sql)
      (jdbc/execute-one! db query-sql {:builder-fn jdbc-rs/as-unqualified-kebab-maps
                                       :return-keys true}))))

; Component

(defmethod ig/assert-key ::db
  [_ params]
  (ig-extras/validate-schema!
    {:component ::db
     :data params
     :schema [:map
              [:jdbc-url string?]]}))

(defmethod ig/init-key ::db
  [_ options]
  (log/info "[DB] Starting database connection pool...")
  (let [datasource (cp/make-datasource options)]
    (ragtime-repl/migrate
      {:datastore (ragtime-jdbc/sql-database datasource)
       :migrations (ragtime-jdbc/load-resources "migrations")})
    datasource))

(defmethod ig/halt-key! ::db
  [_ datasource]
  (log/info "[DB] Closing database connection pool...")
  (cp/close-datasource datasource))
