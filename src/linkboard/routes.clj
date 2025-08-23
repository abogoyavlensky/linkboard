(ns linkboard.routes
  (:require [linkboard.account.handlers :as account-handlers]
            [linkboard.board.handlers :as board-handlers]
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
         :parameters {:query [:map [:page {:optional true} pos-int?]]}
         :get {:handler home-handlers/home-handler
               :responses {200 {:body string?}}}}]
   ["/up" {:name ::health-check
           :get {:handler (fn [_] (response/response "OK"))}}]
   ["/create-account" {:name ::create-account
                       ; Rate limit login attempts to 3 per minute per IP
                       :middleware [[limits/wrap-rate-limit 10 60000]]
                       :post {:handler home-handlers/create-account-handler
                              :parameters {:form {:account-number [:string {:min 1}]}}
                              :responses {200 {:body string?}}}}]
   ["/login" {:name ::login
              ; Rate limit login attempts to 10 per minute per IP
              :middleware [[limits/wrap-rate-limit 20 60000]]
              :post {:handler home-handlers/login-handler
                     :parameters {:form {:account-number [:string {:min 1}]}}
                     :responses {200 {:body string?}}}}]
   ["/logout" {:name ::logout
               :post {:handler home-handlers/logout-handler
                      :responses {200 {:body string?}}}}]
   ["/account" {:name ::account
                :get {:handler account-handlers/account-handler
                      :responses {200 {:body string?}}}
                :delete {:handler account-handlers/delete-account-handler
                         :responses {200 {:body nil?}}}}]
   ["/account/export" {:name ::export-data
                       :get {:handler account-handlers/export-data-handler
                             :responses {200 {:body string?}}}}]
   ["/links"
    ["" {:name ::links
         :parameters {:query [:map
                              [:page {:optional true} pos-int?]
                              [:q {:optional true} [:string {:min 1}]]]}
         :get {:handler board-handlers/all-links-handler
               :responses {200 {:body string?}}}
         :post {:handler home-handlers/create-link-handler
                :parameters {:form [:map
                                    [:url spec/Link]
                                    [:title {:optional true} :string]
                                    [:board {:optional true} pos-int?]]}}}]
    ["/:link-id"
     {:parameters {:path {:link-id pos-int?}}}
     ["" {:name ::link-details
          :put {:handler board-handlers/update-link-handler
                :parameters {:form {:title [:string {:min 1}]
                                    :url spec/Link}}}
          :delete {:handler board-handlers/delete-link-handler}}]
     ["/favorite" {:name ::toggle-link-favorite
                   :patch {:handler board-handlers/toggle-link-favorite-handler
                           :responses {200 {:body string?}}}}]]]

   ["/boards"
    ["" {:name ::board-list
         :post {:handler home-handlers/create-board-handler
                :parameters {:form {:title [:string {:min 1}]}}
                :responses {200 {:body string?}}}}]
    ["/:id"
     {:parameters {:path {:id pos-int?}
                   :query [:map
                           [:page {:optional true} pos-int?]
                           [:q {:optional true} [:string {:min 1}]]]}}
     ["" {:name ::board-details
          :get {:handler board-handlers/board-handler
                :responses {200 {:body string?}}}
          :put {:handler board-handlers/update-board-handler
                :parameters {:form {:title [:string {:min 1}]}}}
          :delete {:handler board-handlers/delete-board-handler
                   :responses {200 {:body nil?}}}}]
     ["/favorite" {:name ::toggle-board-favorite
                   :patch {:handler home-handlers/toggle-board-favorite-handler
                           :responses {200 {:body string?}}}}]]]])
