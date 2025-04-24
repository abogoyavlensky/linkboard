(ns linkboard.handlers
  (:require [linkboard.ui.components :as components]
            [reitit-extras.core :as reitit-extras]
            [ring.util.response :as response]))

(defn default-handler
  [error-text status-code]
  (fn [request]
    (-> (components/error-page request error-text)
        (reitit-extras/render-html)
        (response/status status-code))))
