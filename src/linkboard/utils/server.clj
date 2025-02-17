(ns linkboard.utils.server
  "Useful router middlewares."
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [hiccup2.core :as h]
            [reitit.ring :as ring]
            [reitit.ring.middleware.exception :as exception]
            [ring.middleware.gzip :as gzip]
            [ring.util.response :as response])
  (:import [java.security MessageDigest]))

; Middlewares

(defn wrap-context
  "Add system dependencies of handler to request as a context key."
  [handler context]
  (fn [request]
    (-> request
      (assoc :context context)
      (handler))))

(defn string->16-byte-array [s]
  (let [digest (MessageDigest/getInstance "MD5")]
    (.digest digest (.getBytes s "UTF-8"))))

; Handlers

(def ^:private cache-30d "public,max-age=2592000,immutable")

(defn- resource-response-cached
  ([path]
   (resource-response-cached path {}))
  ([path options]
   (-> (response/resource-response path options)
     (response/header "Cache-Control" cache-30d))))

(defn create-resource-handler-cached
  "Return resource handler with optional Cache-Control header."
  [{:keys [cached?]
    :as opts}]
  (let [response-fn (if cached?
                      resource-response-cached
                      response/resource-response)]
    (-> response-fn
      (ring/-create-file-or-resource-handler opts)
      (gzip/wrap-gzip))))

(defn render-html
  [content]
  (-> content
    (h/html)
    (str)
    (response/response)
    (response/header "Content-Type" "text/html")))

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
  [handler e request]
  (log/error (pr-str (:request-method request) (:uri request)) (ex-message e))
  (handler e request))

(def exception-middleware
  "Common exception middleware to handle all errors."
  (exception/create-exception-middleware
    (merge
      exception/default-handlers
      {; override the default handler
       ::exception/default (partial default-error-handler "UnexpectedError")

       ; print stack-traces for all exceptions
       ::exception/wrap wrap-exception})))
