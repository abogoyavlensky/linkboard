(ns linkboard.core
  (:gen-class)
  (:require [integrant-extras.core :as ig-extras]
            [resauce.core :as resauce]))

(defn -main
  "Run application system in production env."
  []

  (prn (resauce/resource-dir "public/css"))

  #_(ig-extras/run-system {:profile :prod}))
