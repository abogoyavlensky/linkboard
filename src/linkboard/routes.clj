(ns linkboard.routes
  (:require [linkboard.board-page :as board-page]
            [linkboard.home-page :as home-page]
            [ring.util.response :as response]))

(def routes
  [["/" {:name ::home-page
         :get {:handler home-page/home-handler
               :responses {200 {:body string?}}}}]
   ["/boards"

    ["" {:name ::board-list
         :post {:handler home-page/create-board-handler
                :parameters {:form {:title [:string {:min 1}]}}
                :responses {200 {:body string?}}}}]
    ["/:id" {:name ::board-details
             :get {:handler board-page/board-handler
                   :parameters {:path {:id pos-int?}}
                   :responses {200 {:body string?}}}}]]
   ["/up" {:name ::health-check
           :get {:handler (fn [_] (response/response "OK"))}}]])
