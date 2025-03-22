(ns linkboard.handlers
  (:require [linkboard.index :as index]
            [reitit-extras.core :as reitit-extras]
            [ring.util.response :as response]))

(defn index-handler
  [_]
  (reitit-extras/render-html index/starter-page))

(defn default-handler
  [error-text status-code]
  (fn [_]
    (-> (index/error-page error-text)
        (reitit-extras/render-html)
        (response/status status-code))))
