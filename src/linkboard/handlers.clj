(ns linkboard.handlers
  (:require [linkboard.index :as index]
            [reitit-extras.core :as reitit-extras]))

(defn index-handler
  [_]
  (reitit-extras/render-html index/starter-page))
