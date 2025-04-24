(ns linkboard.routes
  (:require [clj-ulid :as ulid]
            [clojure.string :as str]
            [linkboard.board.handlers :as board-handlers]
            [linkboard.home.handlers :as home-handlers]
            [ring.util.response :as response]))

(defn wrap-sync-code
  "Middleware that manages the sync-code in session.
   - Adds a random UUID as :sync-code to request if not present in session
   - Transfers :sync-code from request to session in response if present"
  [handler]
  (fn [request]
    (let [session (:session request)
          has-sync-code? (boolean (:sync-code session))
          updated-request (if has-sync-code?
                            request
                            (assoc-in request [:session :sync-code] (str/upper-case (ulid/ulid))))
          response (handler updated-request)]
      (if (not has-sync-code?)
        (assoc response :session {:sync-code (get-in updated-request [:session :sync-code])})
        response))))

(def routes
  [["/" {:name ::home-page
         :middleware [wrap-sync-code]
         :get {:handler home-handlers/home-handler
               :responses {200 {:body string?}}}}]
   ["/up" {:name ::health-check
           :get {:handler (fn [_] (response/response "OK"))}}]
   ["/boards" {:middleware [wrap-sync-code]}
    ["" {:name ::board-list
         :post {:handler home-handlers/create-board-handler
                :parameters {:form {:title [:string {:min 1}]}}
                :responses {200 {:body string?}}}}]
    ["/:id"
     ["" {:name ::board-details
          :get {:handler board-handlers/board-handler
                :parameters {:path {:id pos-int?}}
                :responses {200 {:body string?}}}
          :put {:handler board-handlers/update-board-handler
                :parameters {:path {:id pos-int?}
                             :form {:title [:string {:min 1}]}}
                :responses {200 {:body string?}}}
          :delete {:handler board-handlers/delete-board-handler
                   :parameters {:path {:id pos-int?}}
                   :responses {200 {:body nil?}}}}]
     ["/links"
      ["" {:name ::board-details-links
           :post {:handler board-handlers/add-link-handler
                  :parameters {:form {:url [:string {:min 1}]}}
                  :responses {200 {:body string?}}}}]
      ["/:link-id" {:name ::link-details
                    :put {:handler board-handlers/update-link-handler
                          :parameters {:path {:id pos-int?
                                              :link-id pos-int?}
                                       :form {:title [:string {:min 1}]
                                              :url [:string {:min 1}]}}}
                    :delete {:handler board-handlers/delete-link-handler
                             :parameters {:path {:id pos-int?
                                                 :link-id pos-int?}}}}]]]]])
