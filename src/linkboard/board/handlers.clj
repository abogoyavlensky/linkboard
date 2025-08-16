(ns linkboard.board.handlers
  (:require [linkboard.board.fetch :as fetch]
            [linkboard.board.views :as views]
            [linkboard.core.db :as db]
            [linkboard.queries :as q]
            [linkboard.routes :as-alias r]
            [linkboard.ui.components :as c]
            [reitit-extras.core :as ext]
            [ring.util.response :as response]))

(defn board-handler
  {:malli/schema [:=> [:cat :map] :map]}
  [{{:keys [db]} :context
    {:keys [path]} :parameters
    :keys [session]
    :as request}]
  (let [user (q/get-user-by-session-id db (:session-id session))
        board (->> {:select [:*]
                    :from [:board]
                    :where [:and
                            [:= :id (:id path)]
                            [:= :user-id (:id user)]]}
                   (db/exec-one! db))
        ; TODO: add pagination
        links (->> {:select [:l.*]
                    :from [[:link :l]]
                    :join [[:board :b] [:= :l.board-id :b.id]]
                    :where [:and
                            [:= :b.user-id (:id user)]
                            [:= :b.id (:id path)]]
                    :order-by [[:l.created-at :desc]]}
                   (db/exec! db))
        request* (assoc request :board-id (:id board))
        page-view (->> (views/board-view request* {:board board
                                                   :links links})
                       (c/body request*))]

    (if (c/hx-request? request)
      (ext/render-html page-view)
      (->> page-view
           (c/base)
           (ext/render-html)))))

(defn all-links-handler
  [{{:keys [db]} :context
    :keys [session]
    :as request}]
  (let [user (q/get-user-by-session-id db (:session-id session))
        ; TODO: add pagination
        links (->> {:select [:l.* [:b.title :board-title]]
                    :from [[:link :l]]
                    :left-join [[:board :b] [:= :l.board-id :b.id]]
                    :where [:= :l.user-id (:id user)]
                    :order-by [[:l.created-at :desc]]}
                   (db/exec! db))
        page-view (->> (views/all-links-view request {:links links})
                       (c/body request))]
    (if (c/hx-request? request)
      (ext/render-html page-view)
      (->> page-view
           (c/base)
           (ext/render-html)))))

(defn update-link-handler
  [{{:keys [db]} :context
    {:keys [form path]} :parameters
    :keys [session errors]
    router :reitit.core/router
    :as request}]
  (cond
    (not (q/user-owns-link? db {:link-id (-> path :link-id)
                                :session-id (:session-id session)}))
    (-> (response/response "Link not found or access denied")
        (response/status 403))

    (seq errors)
    (-> (views/link-edit-form-fields request {:link form})
        (ext/render-html)
        (response/status 400))

    :else
    (let [link-id (-> path :link-id)
          title (:title form)
          url (:url form)
          user (q/get-user-by-session-id db (:session-id session))
          metadata (fetch/fetch-page-metadata url)
          _ (->> {:update :link
                  :set {:title title
                        :url url
                        :icon (:icon metadata)}
                  :where [:and
                          [:= :id link-id]
                          [:= :user-id (:id user)]]}
                 (db/exec-one! db))
          ; Get the complete updated link
          updated-link (->> {:select [:*]
                             :from [:link]
                             :where [:and
                                     [:= :id link-id]
                                     [:= :user-id (:id user)]]}
                            (db/exec-one! db))]
      (-> (views/link-list-item {:request request
                                 :router router
                                 :link updated-link})
          (ext/render-html)
          (response/header "HX-Trigger" "showLinkEditToast")
          (response/header "HX-Trigger-After-Swap" "modal-close")))))

(defn update-board-handler
  [{{:keys [db]} :context
    {:keys [form]} :parameters
    router :reitit.core/router
    :keys [parameters session errors]
    :as request}]
  (if (seq errors)
    (-> (views/board-edit-form-fields request {:board form})
        (ext/render-html))
    (let [user (q/get-user-by-session-id db (:session-id session))
          board-id (-> parameters :path :id)
          title (:title form)]
      ; Update board in the database
      (->> {:update :board
            :set {:title title}
            :where [:and
                    [:= :id board-id]
                    [:= :user-id (:id user)]]}
           (db/exec-one! db))
      ; Render updated board content
      (-> (response/response [:div])
          (response/header "HX-Redirect"
                           (ext/get-route router ::r/board-details {:path {:id board-id}}))
          (response/header "HX-Trigger" "showBoardEditToast")))))

(defn delete-board-handler
  [{{:keys [db]} :context
    :keys [path-params session]}]
  (let [user (q/get-user-by-session-id db (:session-id session))
        board-id (-> path-params :id parse-long)]
    ; Delete board (this will cascade delete all links in the board)
    (->> {:delete-from :board
          :where [:and
                  [:= :id board-id]
                  [:= :user-id (:id user)]]}
         (db/exec-one! db))
    ; Redirect to home page
    (-> (response/response nil)
        (response/header "HX-Redirect" "/")
        (response/header "HX-Trigger" "showBoardDeletionToast"))))

(defn delete-link-handler
  [{{:keys [db]} :context
    {:keys [path]} :parameters
    :keys [session]}]
  (cond
    (not (q/user-owns-link? db {:link-id (:link-id path)
                                :session-id (:session-id session)}))
    (-> (response/response "Link not found or access denied")
        (response/status 403))

    :else
    (let [user (q/get-user-by-session-id db (:session-id session))]
      (q/delete-link! db {:link-id (:link-id path)
                          :user-id (:id user)})
      (-> (response/response nil)
          (response/header "HX-Trigger-After-Swap" "modal-close, show-link-deletion-toast")))))
