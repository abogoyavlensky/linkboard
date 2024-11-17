(ns linkboard.utils.server
  "Useful router middlewares."
  (:require [hiccup2.core :as h]
            [reitit.ring :as ring]
            [ring.middleware.gzip :as gzip]
            [ring.util.response :as response]))

; Middlewares

(defn wrap-context
  "Add system dependencies of handler to request as a context key."
  [handler context]
  (fn [request]
    (-> request
      (assoc :context context)
      (handler))))

(defn wrap-reload
  "Reload ring handler on every request. Useful in dev mode."
  [f]
  ; Require reloader locally to exclude dev dependency from prod build
  (let [reload! ((requiring-resolve 'ring.middleware.reload/reloader) ["src"] true)]
    (fn
      ([request]
       (reload!)
       ((f) request))
      ([request respond raise]
       (reload!)
       ((f) request respond raise)))))

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
