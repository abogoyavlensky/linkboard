(ns linkboard.home.handlers
  (:require [clojure.string :as str]
            [linkboard.board.fetch :as fetch]
            [linkboard.board.pagination :as pagination]
            [linkboard.board.views :as board-views]
            [linkboard.core.db :as db]
            [linkboard.home.views :as views]
            [linkboard.queries :as queries]
            [linkboard.routes :as-alias r]
            [linkboard.ui.components :as c]
            [reitit-extras.core :as ext]
            [ring.util.response :as response]))

(def ^:const DEFAULT-BOARD-LIMIT 50)
(def ^:const DEFAULT-LINK-LIMIT 1000)

(defn home-handler
  {:malli/schema [:=> [:cat :map] :map]}
  [{{:keys [db]} :context
    :keys [session]
    :as request}]
  (let [user (queries/get-user-by-session-id db (:session-id session))
        all-links-count (->> {:select [[[:count :id] :links-count]]
                              :from [:link]
                              :where [:= :user-id (:id user)]}
                             (db/exec-one! db)
                             :links-count)
        page (pagination/get-page-param request)
        boards-query {:select [:b.*
                               [[:count :l.id] :link-count]]
                      :from [[:board :b]]
                      :left-join [[:link :l] [:= :b.id :l.board-id]]
                      :where [:= :b.user-id (:id user)]
                      :group-by [:b.id :b.title :b.favorite]
                      :order-by [[:b.favorite :desc] [:b.created-at :desc]]}
        boards (->> (pagination/add-pagination boards-query page)
                    (db/exec! db)
                    (mapv (fn [v] (update v :favorite #(> % 0)))))
        board-count (->> {:select [[[:count :id] :board-count]]
                          :from [:board]
                          :where [:= :user-id (:id user)]}
                         (db/exec-one! db)
                         :board-count)
        has-more? (pagination/has-more-pages? board-count page)
        route "/"]
    (cond
      (not (c/hx-request? request))
      ; Full page response
      (->> (views/boards-view request {:boards boards
                                       :all-links-count all-links-count
                                       :has-more? has-more?
                                       :route route
                                       :page page})
           (c/body request)
           (c/base)
           (ext/render-html))

      (pagination/pagination-request? request)
      ; Pagination response - just boards + trigger fragment
      (->> (views/board-pagination-view request {:boards boards
                                                 :has-more? has-more?
                                                 :route route
                                                 :page page})
           (ext/render-html))

      :else
      ; Standard HTMX page response
      (->> (views/boards-view request {:boards boards
                                       :all-links-count all-links-count
                                       :has-more? has-more?
                                       :route route
                                       :page page})
           (c/body request)
           (ext/render-html)))))

(defn create-board-handler
  {:malli/schema [:=> [:cat :map] :map]}
  [{{:keys [db]} :context
    {:keys [form]} :parameters
    router :reitit.core/router
    :keys [session errors]
    :as request}]
  (if (seq errors)
    (-> (views/board-form-fields request)
        (ext/render-html))
    (let [user (queries/ensure-user-exists! db (:session-id session))
          board-count (queries/get-user-board-count db (:id user))]
      (if (>= board-count DEFAULT-BOARD-LIMIT)
        ; Return 422 status with error message
        (-> (views/board-form-fields (assoc-in request [:errors :humanized :title] ["Board limit reached. You can have up to 50 boards."]))
            (ext/render-html)
            (response/status 200)
            (response/header "HX-Trigger-After-Swap" "modal-close")
            (response/header "HX-Trigger" "showBoardLimitReachedToast"))
        ; Create a new board
        (let [board (->> {:insert-into :board
                          :values [{:title (:title form)
                                    :user-id (:id user)}]
                          :returning [:*]}
                         (db/exec-one! db))]
          (-> (ext/render-html (list ; Return fresh form
                                 (views/board-form-fields {})
                                     ; Add item to the top of the board list
                                 [:div
                                  {:hx-swap-oob "afterbegin:#board-list"}
                                  (views/list-item {:router router
                                                    :board board})]
                                 ; Remove empty state
                                 [:div
                                  {:hx-swap-oob "delete:#empty-boards"}]))

              (response/header "HX-Trigger" "showBoardCreationToast")
              (response/header "HX-Trigger-After-Swap" "modal-close")))))))

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
            (response/header "HX-Redirect" (ext/route router ::r/home-page))
            (response/header "HX-Trigger" "showRegistrationToast")))

      (:account-number user)
      (-> (response/response "User already exists with an account number.")
          (response/status 400))

      :else
      ; Update existing user's empty account number with actual hashed account number
      (let [updated-user (queries/update-user-account-number! db (:id user) account-number)
            identity-data (select-keys updated-user [:id :session-id])]
        (-> (ext/render-html [:div])
            (assoc :session (assoc session :identity identity-data))
            (response/header "HX-Redirect" (ext/route router ::r/home-page))
            (response/header "HX-Trigger" "showRegistrationToast"))))))

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
          (response/header "HX-Redirect" (ext/route router ::r/home-page)))
      ; If user not found, return an error response
      (-> request
          (assoc-in [:errors :humanized :account-number] ["Invalid account number"])
          (c/login-form-fields)
          (ext/render-html)))))

(defn create-link-handler
  {:malli/schema [:=> [:cat :map] :map]}
  [{{:keys [db]} :context
    {:keys [form]} :parameters
    :keys [errors session]
    router :reitit.core/router
    :as request}]
  (cond
    (seq errors)
    ; Return form validation errors
    (-> (c/link-form-fields (assoc request :board-id (:board form)))
        (ext/render-html))

    :else
    (let [user (queries/ensure-user-exists! db (:session-id session))
          link-count (queries/get-user-link-count db (:id user))]
      (if (>= link-count DEFAULT-LINK-LIMIT)
        ; Return 200 status with error message
        (-> (c/link-form-fields (assoc-in request [:errors :humanized :url] ["Link limit reached. You can have up to 1000 links."]))
            (ext/render-html)
            (response/status 200)
            (response/header "HX-Trigger-After-Swap" "modal-close")
            (response/header "HX-Trigger" "showLinkLimitReachedToast"))
        (let [board-id (:board form)
              user-title (str/trim (:title form))
              metadata (fetch/fetch-page-metadata (:url form))
              ; Use user-provided title or fallback to metadata title
              final-title (if (and user-title (not (str/blank? user-title)))
                            user-title
                            (:title metadata))]
          ; Validate that if board_id is provided, the user owns that board
          (if (and board-id (not (queries/user-owns-board? db {:board-id board-id
                                                               :session-id (:session-id session)})))
            (response/status 403)
            (let [boards (queries/get-user-boards-minimal db (:id user))
                  link (->> {:insert-into :link
                             :values [{:url (:url form)
                                       :title final-title
                                       :icon (:icon metadata)
                                       :board-id board-id
                                       :user-id (:id user)}]
                             :returning [:*]}
                            (db/exec-one! db))]
              (-> (ext/render-html (list (c/link-form-fields {})
                                         [:div
                                          ; Add item to the top of the link list
                                          {:hx-swap-oob "afterbegin:#link-list"}
                                          (board-views/link-list-item {:request request
                                                                       :router router
                                                                       :link link
                                                                       :boards boards})]
                                         ; Remove empty state
                                         [:div
                                          {:hx-swap-oob "delete:#empty-links"}]))
                  (response/header "HX-Refresh" "true")
                  (response/header "HX-Trigger" "showLinkCreationToast")
                  (response/header "HX-Trigger-After-Swap" "modal-close")))))))))

(defn logout-handler
  {:malli/schema [:=> [:cat :map] :map]}
  [{router :reitit.core/router
    :keys [_session]}]
  (-> (ext/render-html [:div])
      (response/header "HX-Redirect" (ext/route router ::r/home-page))
      (assoc :session nil)))

(defn toggle-board-favorite-handler
  {:malli/schema [:=> [:cat :map] :map]}
  [{{:keys [db]} :context
    {:keys [path]} :parameters
    :keys [session]
    :as _request}]
  (let [board-id (:id path)
        user (queries/get-user-by-session-id db (:session-id session))]
    (if (queries/user-owns-board? db {:board-id board-id
                                      :session-id (:session-id session)})
      (let [updated-board (queries/toggle-board-favorite! db {:board-id board-id
                                                              :user-id (:id user)})]
        (-> (ext/render-html (views/favorite-icon updated-board))
            (response/header "HX-Trigger" (if (:favorite updated-board)
                                            "showBoardFavoriteAddedToast"
                                            "showBoardFavoriteRemovedToast"))))
      (response/status 403))))
