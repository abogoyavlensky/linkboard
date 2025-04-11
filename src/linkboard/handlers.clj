(ns linkboard.handlers
  (:require [linkboard.components :as components]
            [reitit-extras.core :as reitit-extras]
            [ring.util.response :as response]))

(defn default-handler
  [error-text status-code]
  (fn [_]
    (-> (components/error-page error-text)
        (reitit-extras/render-html)
        (response/status status-code))))
