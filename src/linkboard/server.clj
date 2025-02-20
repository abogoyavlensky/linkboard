(ns linkboard.server
  (:require [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [linkboard.routes :as app-routes]
            [linkboard.utils.server :as server-utils]
            [linkboard.utils.system :as system-utils]
            [muuntaja.core :as muuntaja-core]
            [reitit.coercion.malli :as coercion-malli]
            [reitit.dev.pretty :as pretty]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as ring-coercion]
            [reitit.ring.middleware.multipart :as ring-multipart]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as ring-parameters]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.anti-forgery :as ring-anti-forgery]
            [ring.middleware.cookies :as ring-cookies]
            [ring.middleware.session :as ring-session]
            [ring.middleware.session.cookie :as ring-session-cookie])
  (:import com.zaxxer.hikari.HikariDataSource))

(defn- handler
  "Return main application handler."
  [{:keys [options]
    :as context}]
  (ring/ring-handler
    (ring/router
      app-routes/routes
      {:exception pretty/exception
       :data {:muuntaja muuntaja-core/instance
              :coercion coercion-malli/coercion
              :middleware [; enable cookies
                           ring-cookies/wrap-cookies

                           ; TODO: move to the top level middleware!
                           ; store session in cookies
                           [ring-session/wrap-session
                            {:cookie-attrs {:secure true
                                            :http-only true}
                             :store (ring-session-cookie/cookie-store
                                      {:key (-> options
                                              :session-secret-key
                                              server-utils/string->16-byte-array)})}]

                           ; add handler options to request
                           [server-utils/wrap-context context]
                           ; parse any request parameters
                           ring-parameters/parameters-middleware
                           ; send files
                           ring-multipart/multipart-middleware
                           ; negotiate request and response
                           muuntaja/format-middleware
                           ; Check CSRF token
                           ; add call (linkboard.components/csrf-token) to a form
                           ring-anti-forgery/wrap-anti-forgery
                           ; handle exceptions
                           server-utils/exception-middleware
                           ; coerce request and response to spec
                           ring-coercion/coerce-request-middleware
                           ring-coercion/coerce-response-middleware]}})
    (ring/routes
      (server-utils/create-resource-handler-cached {:path "/assets/"
                                                    :cached? (:cache-assets? options)
                                                    :cache-control (:cache-control options)})
      (ring/redirect-trailing-slash-handler)
      (ring/create-default-handler
        {:not-found (fn [_]
                      {:status 404
                       ; TODO: add common html!
                       :body "Not found"})}))))

(defmethod ig/assert-key ::server
  [_ params]
  (system-utils/validate-schema!
    {:data params
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
                    #(instance? HikariDataSource %)]]]
     :error-message (format "Invalid %s component config" ::server)}))

(defmethod ig/init-key ::server
  [_ {:keys [options]
      :as context}]
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
