(ns linkboard.handlers
  (:require [linkboard.index :as index]
            [reitit-extras.core :as reitit-extras]))

(defn index-handler
  [_]
  (-> index/starter-page
    (index/base)
    (reitit-extras/render-html)))
