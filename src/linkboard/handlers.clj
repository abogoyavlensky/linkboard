(ns linkboard.handlers
  (:require [linkboard.index :as index]
            [ring.util.response :as response]
            [reitit-extras.core :as reitit-extras]))

(defn index-handler
  [_]
  (reitit-extras/render-html index/starter-page))

(defn default-handler
  [error-text]
  (fn [_]
    (-> (index/error-page error-text)
        (reitit-extras/render-html)
        (response/status 500))))
