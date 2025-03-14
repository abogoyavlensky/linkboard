(ns linkboard.server
  (:require [clojure.tools.logging :as log]
            [integrant-extras.core :as ig-extras]
            [integrant.core :as ig]
            [linkboard.routes :as app-routes]
            [reitit-extras.core :as reitit-extras]
            [ring.adapter.jetty :as jetty]
            [linkboard.handlers :as handlers])
  (:import com.zaxxer.hikari.HikariDataSource))

(defmethod ig/assert-key ::server
  [_ params]
  (ig-extras/validate-schema!
    {:component ::server
     :data params
     :schema [:map
              [:options
               [:map
                [:port pos-int?]
                [:session-secret-key string?]
                [:auto-reload? boolean?]
                [:cache-assets? {:optional true} boolean?]
                [:cache-control {:optional true} symbol?]]]
              [:db [:fn
                    {:error/message "Wrong db datasource type"}
                    #(instance? HikariDataSource %)]]]}))

(defmethod ig/init-key ::server
  [_ {:keys [options]
      :as context}]
  (log/info (str "[SERVER] Starting server..."))
  (-> {:routes app-routes/routes
       :default-handlers {:not-found handlers/page-not-found}}
    (reitit-extras/get-handler-ssr context)
    (jetty/run-jetty {:port (:port options)
                      :join? false})))

(defmethod ig/halt-key! ::server
  [_ server]
  (log/info (str "[SERVER] Stopping server..."))
  (.stop server))
