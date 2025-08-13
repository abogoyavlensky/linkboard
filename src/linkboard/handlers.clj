(ns linkboard.handlers
  (:require [clojure.string :as str]
            [linkboard.ui.components :as components]
            [reitit-extras.core :as reitit-extras]
            [ring.util.response :as response]))

(defn- devtools-request?
  "Check if the request is from Chrome DevTools or similar development tools"
  [request]
  (let [uri (:uri request)]
    (or (str/starts-with? uri "/.well-known/appspecific/")
        (str/starts-with? uri "/chrome-devtools-frontend")
        (= uri "/favicon.ico"))))

(defn default-handler
  [error-text status-code]
  (fn [request]
    (if (devtools-request? request)
      ; Simple response for DevTools requests to avoid triggering full error page
      (response/status (response/response "") status-code)
      ; Full error page for actual user-facing errors
      (-> (components/error-page request error-text)
          (reitit-extras/render-html)
          (response/status status-code)))))
