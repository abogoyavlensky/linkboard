(ns linkboard.home.handlers
  (:require [linkboard.core.db :as db]
            [linkboard.home.views :as views]
            [linkboard.queries :as queries]
            [linkboard.routes :as-alias r]
            [linkboard.ui.components :as c]
            [reitit-extras.core :as ext]
            [ring.util.response :as response]))

(defn home-handler
  {:malli/schema [:=> [:cat :map] :map]}
  [{{:keys [db]} :context
    :keys [session]
    :as request}]
  (let [user (queries/get-user-by-session-id db (:session-id session))
        all-links-count (->> {:select [[[:count :l.id] :links-count]]
                              :from [[:board :b]]
                              :join [[:link :l] [:= :b.id :l.board-id]]
                              :where [:= :b.user-id (:id user)]}
                             (db/exec-one! db)
                             :links-count)
        ; TODO: add pagination
        boards (db/exec! db {:select [:b.*
                                      [[:count :l.id] :link-count]]
                             :from [[:board :b]]
                             :left-join [[:link :l] [:= :b.id :l.board-id]]
                             :where [:= :b.user-id (:id user)]
                             :group-by [:b.id :b.title]
                             :order-by [[:b.created_at :desc]]})
        page-view (views/boards-view request {:boards boards
                                              :all-links-count all-links-count})]
    (if (c/hx-request? request)
      (ext/render-html page-view)
      (->> page-view
           (c/base request)
           (ext/render-html)))))

(defn create-board-handler
  {:malli/schema [:=> [:cat :map] :map]}
  [{{:keys [db]} :context
    {:keys [form]} :parameters
    router :reitit.core/router
    :keys [session errors]
    :as request}]
  (if (seq errors)
    (->> (views/board-form-fields request)
         (ext/render-html))
    (let [user (queries/ensure-user-exists! db (:session-id session))]
      ; Create a new board
      (->> {:insert-into :board
            :values [{:title (:title form)
                      :user-id (:id user)}]}
           (db/exec-one! db))
      (-> (ext/render-html [:div])
          (response/header "HX-Redirect" (ext/get-route router ::r/home-page))))))

(defn create-account-handler
  {:malli/schema [:=> [:cat :map] :map]}
  [{{:keys [db]} :context
    {:keys [form]} :parameters
    router :reitit.core/router
    :keys [session]}]
  (let [user (queries/get-user-by-session-id db (:session-id session))
        account-number (:account-number form)]
    (cond
      (not user)
      ; Create new user with session-id and hashed account number
      (let [created-user (queries/create-user! db (:session-id session) account-number)
            identity-data (select-keys created-user [:id :session-id])]
        (-> (ext/render-html [:div])
            (assoc :session (assoc session :identity identity-data))
            (response/header "HX-Redirect" (ext/get-route router ::r/home-page))))

      (:account-number user)
      (-> (response/response "User already exists with an account number.")
          (response/status 400))

      :else
      ; Update existing user's empty account number with actual hashed account number
      (let [updated-user (queries/update-user-account-number! db (:id user) account-number)
            identity-data (select-keys updated-user [:id :session-id])]
        (-> (ext/render-html [:div])
            (assoc :session (assoc session :identity identity-data))
            (response/header "HX-Redirect" (ext/get-route router ::r/home-page)))))))

(defn login-handler
  {:malli/schema [:=> [:cat :map] :map]}
  [{{:keys [db]} :context
    {:keys [form]} :parameters
    :keys [session errors]
    router :reitit.core/router
    :as request}]
  (if (seq errors)
    (->> (c/login-form-fields request)
         (ext/render-html))
    (if-let [user (queries/get-user-by-account-number db (:account-number form))]
      (-> (ext/render-html [:div])
          (assoc :session (assoc session :identity (select-keys user [:id :session-id])
                                 :session-id (:session-id user)))
          (response/header "HX-Redirect" (ext/get-route router ::r/home-page)))
      ; If user not found, return an error response
      (-> request
          (assoc-in [:errors :humanized :account-number] ["Invalid account number"])
          (c/login-form-fields)
          (ext/render-html)))))

(defn logout-handler
  {:malli/schema [:=> [:cat :map] :map]}
  [{router :reitit.core/router
    :keys [_session]}]
  (-> (ext/render-html [:div])
      (response/header "HX-Redirect" (ext/get-route router ::r/home-page))
      (assoc :session nil)))
