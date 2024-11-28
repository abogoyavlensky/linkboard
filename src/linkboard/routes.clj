(ns linkboard.routes
  (:require [linkboard.home :as home]
            [ring.util.response :as response]))

(def routes
  [["/" {:name ::home-page
         :get {:handler home/home-handler}}]
   ["/up" {:name ::health-check
           :get {:handler (fn [_] (response/response "OK"))}}]])
