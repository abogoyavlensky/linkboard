(ns linkboard.server
  (:require [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [ring.adapter.jetty :as jetty]
            [reitit.dev.pretty :as pretty]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as ring-coercion]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [reitit.ring.middleware.exception :as exception]
            [reitit.coercion.malli :as coercion-malli]
            [muuntaja.core :as muuntaja-core]
            [ring.middleware.gzip :as gzip]
            [ring.util.response :as response]
            [linkboard.utils.system :as system-utils]
            [linkboard.utils.server :as server-utils])
  (:import com.zaxxer.hikari.HikariDataSource))

(defn- handler
  "Return main application handler."
  [{:keys [options] :as context}]
  (ring/ring-handler
    (ring/router
      [["/" {:name ::home-page
             :get {:handler (fn [_] (response/response "<h1>OK</h1>"))}}]
       ["/health" {:name ::health-check
                   :get {:handler (fn [_] (response/response "OK"))}}]]
      {:exception pretty/exception
       :data {:muuntaja muuntaja-core/instance
              :coercion coercion-malli/coercion
              :middleware [gzip/wrap-gzip
                           ; add handler options to request
                           [server-utils/wrap-context context]
                           ; parse any request parameters
                           parameters/parameters-middleware
                           ; negotiate request and response
                           muuntaja/format-middleware
                           ; handle exceptions
                           exception/exception-middleware
                           ; coerce request and response to spec
                           ring-coercion/coerce-request-middleware
                           ring-coercion/coerce-response-middleware]}})
    (ring/routes
      (server-utils/create-resource-handler-cached
        {:path "/assets/"
         :cached? (:cache-assets? options)})
      (ring/redirect-trailing-slash-handler)
      ; TODO: add error pages
      (ring/create-default-handler))))


(defmethod ig/assert-key ::server
  [_ params]
  (system-utils/validate-schema!
    {:data params
     :schema [:map
              [:options
               [:map
                [:port pos-int?]
                [:auto-reload? boolean?]
                [:cache-assets? boolean?]]]
              [:db [:fn
                    {:error/message "Wrong db datasource type"}
                    #(instance? HikariDataSource %)]]]
     :error-message (format "Invalid %s component config" ::server)}))


(defmethod ig/init-key ::server
  [_ {:keys [options] :as context}]
  (log/info (str "[SERVER] Starting server..."))
  (let [ring-handler (if (:auto-reload? options)
                       (server-utils/wrap-reload #(handler context))
                       (handler context))]
    (jetty/run-jetty ring-handler {:port (:port options)
                                   :join? false})))


(defmethod ig/halt-key! ::server
  [_ server]
  (log/info (str "[SERVER] Stopping server..."))
  (.stop server))
