(ns linkboard.routes
  (:require [linkboard.board.handlers :as board-handlers]
            [linkboard.home.handlers :as home-handlers]
            [ring.util.response :as response]))

(def routes
  [["/" {:name ::home-page
         :get {:handler home-handlers/home-handler
               :responses {200 {:body string?}}}}]
   ["/up" {:name ::health-check
           :get {:handler (fn [_] (response/response "OK"))}}]
   ["/boards"
    ["" {:name ::board-list
         :post {:handler home-handlers/create-board-handler
                :parameters {:form {:title [:string {:min 1}]}}
                :responses {200 {:body string?}}}}]
    ["/:id"
     ["" {:name ::board-details
          :get {:handler board-handlers/board-handler
                :parameters {:path {:id pos-int?}}
                :responses {200 {:body string?}}}}]
     ["/links"
      ["" {:name ::board-details-links
           :post {:handler board-handlers/add-link-handler
                  :parameters {:form {:url [:string {:min 1}]}}
                  :responses {200 {:body string?}}}}]
      ["/:link-id" {:name ::link-details
                    :delete {:handler board-handlers/delete-link-handler
                             :parameters {:path {:id pos-int?
                                                 :link-id pos-int?}}}}]]]]])
