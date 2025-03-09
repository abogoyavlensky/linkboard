(ns linkboard.core
  (:gen-class)
  (:require [integrant-extras.core :as ig-extras]))

(defn -main
  "Run application system in production env."
  []
  (ig-extras/run-system {:profile :prod}))
