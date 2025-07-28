(ns linkboard.home.handlers
  (:require [buddy.hashers :as hashers]
            [linkboard.core.db :as db]
            [linkboard.home.views :as views]
            [linkboard.queries :as queries]
            [linkboard.routes :as-alias r]
            [linkboard.ui.components :as c]
            [reitit-extras.core :as reitit-extras]
            [ring.util.response :as response]))

; TODO: change to authenticated user
(def USER-ID 1)

(defn home-handler
  {:malli/schema [:=> [:cat :map] :map]}
  [{{:keys [db]} :context
    router :reitit.core/router
    :keys [session]
    :as request}]
  #p session
  (let [all-links-count (->> {:select [[[:count :l.id] :links-count]]
                              :from [[:board :b]]
                              :join [[:link :l] [:= :b.id :l.board-id]]
                              :where [:= :b.user-id USER-ID]}
                             (db/exec-one! db)
                             :links-count)
        ; TODO: add pagination
        boards (db/exec! db {:select [:b.*
                                      [[:count :l.id] :link-count]]
                             :from [[:board :b]]
                             :left-join [[:link :l] [:= :b.id :l.board-id]]
                             :where [:= :b.user-id USER-ID]
                             :group-by [:b.id :b.title]
                             :order-by [[:b.created_at :desc]]})
        page-view (views/boards-view router {:boards boards
                                             :all-links-count all-links-count})]
    (if (c/hx-request? request)
      (reitit-extras/render-html page-view)
      (->> page-view
           (c/base request)
           (reitit-extras/render-html)))))

(defn create-board-handler
  {:malli/schema [:=> [:cat :map] :map]}
  [{{:keys [db]} :context
    {:keys [form]} :parameters
    router :reitit.core/router}]

  ; Create a new board
  (->> {:insert-into :board
        :values [{:title (:title form)
                  :user-id USER-ID}]}
       (db/exec-one! db))
  ; Render home page with a new board in the list
  (let [boards (db/exec! db {:select [:b.*
                                      [[:count :l.id] :link-count]]
                             :from [[:board :b]]
                             :left-join [[:link :l] [:= :b.id :l.board-id]]
                             :where [:= :b.user-id USER-ID]
                             :group-by [:b.id :b.title]
                             :order-by [[:b.created_at :desc]]})]
    (->> {:boards boards}
         (views/board-list router)
         (reitit-extras/render-html))))

(defn create-account-handler
  {:malli/schema [:=> [:cat :map] :map]}
  [{{:keys [db]} :context
    {:keys [form]} :parameters
    :keys [session]}]
  (if-not (:session-id session)
    (-> (response/response "No session found")
        (response/status 400))
    (let [user (queries/get-user-by-session-id db (:session-id session))
          hashed-account-number (hashers/derive (:account-number form) {:alg :bcrypt+sha512})]
      (cond
        (not user)
        ; Create new user with session-id and hashed account number
        (let [created-user (queries/create-user! db (:session-id session) hashed-account-number)
              identity-data (select-keys created-user [:id :session-id])]
          (-> (reitit-extras/render-html [:div])
              (assoc :session (assoc session :identity identity-data))
              (response/header "HX-Redirect" "/")))
            
        (:account-number user)
        (-> (response/response "User already has an account number")
            (response/status 400))
            
        :else
        ; Update existing user with account number
        (let [updated-user (queries/update-user-account-number! db (:id user) hashed-account-number)
              identity-data (select-keys updated-user [:id :session-id])]
          (-> (reitit-extras/render-html [:div])
              (assoc :session (assoc session :identity identity-data))
              (response/header "HX-Redirect" "/")))))))
