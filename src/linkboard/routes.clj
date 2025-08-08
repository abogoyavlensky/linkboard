(ns linkboard.routes
  (:require [linkboard.board.handlers :as board-handlers]
            [linkboard.home.handlers :as home-handlers]
            [linkboard.limits :as limits]
            [linkboard.spec :as spec]
            [ring.util.response :as response]))

(defn wrap-auth
  [handler]
  (fn [{:keys [session]
        :as request}]
    (let [has-session-id? (boolean (:session-id session))
          user (boolean (:identity session))
          request* (cond-> request
                     (not has-session-id?) (assoc-in [:session :session-id] (str (random-uuid)))
                     user (assoc :identity user))
          response (handler request*)]
      (if (not has-session-id?)
        (update response :session assoc :session-id (get-in request* [:session :session-id]))
        response))))

(def routes
  [""
   {:middleware [wrap-auth
                 ; Global rate limit for all routes - 200 requests per minute per IP
                 [limits/wrap-rate-limit 200 60000]]}
   ["/" {:name ::home-page
         :get {:handler home-handlers/home-handler
               :responses {200 {:body string?}}}}]
   ["/up" {:name ::health-check
           :get {:handler (fn [_] (response/response "OK"))}}]
   ["/create-account" {:name ::create-account
                       ; Rate limit login attempts to 3 per minute per IP
                       :middleware [[limits/wrap-rate-limit 3 60000]]
                       :post {:handler home-handlers/create-account-handler
                              :parameters {:form {:account-number [:string {:min 1}]}}
                              :responses {200 {:body string?}}}}]
   ["/login" {:name ::login
              ; Rate limit login attempts to 10 per minute per IP
              :middleware [[limits/wrap-rate-limit 10 60000]]
              :post {:handler home-handlers/login-handler
                     :parameters {:form {:account-number [:string {:min 1}]}}
                     :responses {200 {:body string?}}}}]
   ["/logout" {:name ::logout
               :post {:handler home-handlers/logout-handler
                      :responses {200 {:body string?}}}}]

   ["/boards"
    ["" {:name ::board-list
         :post {:handler home-handlers/create-board-handler
                :parameters {:form {:title [:string {:min 1}]}}
                :responses {200 {:body string?}}}}]
    ["/:id"
     {:parameters {:path {:id pos-int?}}}
     ["" {:name ::board-details
          :get {:handler board-handlers/board-handler
                :responses {200 {:body string?}}}
          :put {:handler board-handlers/update-board-handler
                :parameters {:form {:title [:string {:min 1}]}}}
          :delete {:handler board-handlers/delete-board-handler
                   :responses {200 {:body nil?}}}}]
     ["/links"
      ["" {:name ::board-details-links
           :post {:handler board-handlers/add-link-handler
                  :parameters {:form {:url spec/Link}}}}]
      ["/:link-id" {:name ::link-details
                    :put {:handler board-handlers/update-link-handler
                          :parameters {:path {:id pos-int?
                                              :link-id pos-int?}
                                       :form {:title [:string {:min 1}]
                                              :url spec/Link}}}
                    :delete {:handler board-handlers/delete-link-handler
                             :parameters {:path {:id pos-int?
                                                 :link-id pos-int?}}}}]]]]])
