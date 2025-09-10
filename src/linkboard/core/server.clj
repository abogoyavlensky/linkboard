(ns linkboard.core.server
  (:require [clojure.tools.logging :as log]
            [clojure.string :as str]
            [ring.util.request :as request-util]
            [ring.util.response :as response]
            [integrant-extras.core :as ig-extras]
            [integrant.core :as ig]
            [reitit.ring.middleware.exception :as exception]
            [linkboard.handlers :as handlers]
            [linkboard.routes :as app-routes]
            [linkboard.core.sentry :as sentry]
            [muuntaja.core :as muuntaja-core]
            [reitit-extras.core :as reitit-extras]
            [reitit.coercion.malli :as coercion-malli]
            [reitit.dev.pretty :as pretty]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as ring-coercion]
            [reitit.ring.middleware.multipart :as ring-multipart]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as ring-parameters]
            [sentry-clj.ring :as sentry-ring]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.anti-forgery :as anti-forgery]
            [ring.middleware.content-type :as content-type]
            [ring.middleware.cookies :as ring-cookies]
            [ring.middleware.default-charset :as default-charset]
            [ring.middleware.keyword-params :as keyword-params]
            [ring.middleware.nested-params :as nested-params]
            [ring.middleware.not-modified :as not-modified]
            [ring.middleware.session :as ring-session]
            [ring.middleware.session.cookie :as ring-session-cookie]
            [ring.middleware.ssl :as ring-ssl]
            [ring.middleware.x-headers :as x-headers])
  (:import com.zaxxer.hikari.HikariDataSource))

; Exceptions

(defn- get-error-path
  [exception]
  (mapv
    (comp #(str/join ":" %) :at)
    (:via (Throwable->map exception))))

(defn- default-error-handler
  [error-type exception _request]
  {:status 500
   :body {:type error-type
          :path (get-error-path exception)
          :error (ex-data exception)
          :details (ex-message exception)}})

(defn- wrap-exception
  [{:keys [sentry]}]
  (fn [handler e request]
    (log/error e (pr-str (:request-method request) (:uri request)) (ex-message e))
    (when (= sentry :sentry-initialized)
      (sentry/report-exception! {:message (ex-message e)
                                 :request {:url (request-util/request-url request)
                                           :method (-> request :request-method name)
                                           :query-string (:query-string request "")
                                           :data (:params request)
                                           :env {"REMOTE_ADDR" (:remote-addr request)}
                                           :cookies (:cookies request)
                                           :headers (:headers request)}
                                 :user {:id (-> request :session :identity str)
                                        :other {"session" (-> request :session :session-id str)}}
                                 :throwable e}))
    (-> (handler e request)
        (response/header "HX-Trigger" "showUnexpectedErrorToast"))))

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
                [:env [:enum :dev :prod :test]]
                [:auto-reload? boolean?]
                [:cache-assets? {:optional true} boolean?]
                [:cache-control {:optional true} string?]]]
              [:db [:fn
                    {:error/message "Wrong db datasource type"}
                    #(instance? HikariDataSource %)]]
              [:sentry [:enum :sentry-initialized nil]]]}))

(defn ring-handler
  "Return main application handler for server-side rendering."
  [{:keys [options]
    :as context}]
  (let [session-store (ring-session-cookie/cookie-store
                        {:key (reitit-extras/string->16-byte-array
                                (:session-secret-key options))})
        exception-middleware (exception/create-exception-middleware
                               (merge
                                 exception/default-handlers
                                 {; override the default handler
                                  ::exception/default (partial default-error-handler "UnexpectedError")

                                  ; print stack-traces for all exceptions
                                  ::exception/wrap (wrap-exception context)}))]
    (ring/ring-handler
      (ring/router
        (app-routes/routes (:env options))
        {:exception pretty/exception
         :data {:muuntaja muuntaja-core/instance
                :coercion coercion-malli/coercion
                :middleware [[x-headers/wrap-content-type-options :nosniff]
                             [x-headers/wrap-frame-options :sameorigin]
                             ring-ssl/wrap-hsts
                             reitit-extras/wrap-xss-protection
                             not-modified/wrap-not-modified
                             content-type/wrap-content-type
                             [default-charset/wrap-default-charset "utf-8"]
                             ring-cookies/wrap-cookies
                             [ring-session/wrap-session
                              {:cookie-attrs {:secure true
                                              :http-only true
                                              :same-site :lax
                                              :max-age (* 365 24 60 60 10)} ; 10 years
                               :flash true
                               :store session-store}]
                             ; add handler options to request
                             [reitit-extras/wrap-context context]
                             ; sentry error reporting
                             sentry-ring/wrap-sentry-tracing
                             ; parse any request parameters
                             ring-parameters/parameters-middleware
                             ring-multipart/multipart-middleware
                             nested-params/wrap-nested-params
                             keyword-params/wrap-keyword-params
                             ; negotiate request and response
                             muuntaja/format-middleware
                             ; check CSRF token
                             anti-forgery/wrap-anti-forgery
                             ; handle exceptions
                             exception-middleware
                             ; coerce request and response to spec
                             ring-coercion/coerce-exceptions-middleware
                             reitit-extras/non-throwing-coerce-request-middleware
                             ring-coercion/coerce-response-middleware]}})
      (ring/routes
        (reitit-extras/create-resource-handler-cached {:path "/assets/"
                                                       :cached? (:cache-assets? options)
                                                       :cache-control (:cache-control options)})
        (ring/redirect-trailing-slash-handler)
        (ring/create-default-handler {:not-found (handlers/default-handler "Page not found" 404)
                                      :method-not-allowed (handlers/default-handler "Method not allowed" 405)
                                      :not-acceptable (handlers/default-handler "Not acceptable" 406)})))))

(defmethod ig/init-key ::server
  [_ {:keys [options]
      :as context}]
  (log/info "[SERVER] Starting server...")
  (let [handler-fn #(ring-handler context)
        handler (if (:auto-reload? options)
                  (reitit-extras/wrap-reload handler-fn)
                  (handler-fn))]
    (jetty/run-jetty handler {:port (:port options)
                              :host "0.0.0.0"
                              :join? false})))

(defmethod ig/halt-key! ::server
  [_ server]
  (log/info "[SERVER] Stopping server...")
  (.stop server))
