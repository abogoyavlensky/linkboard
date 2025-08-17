(ns linkboard.board.handlers
  (:require [linkboard.board.fetch :as fetch]
            [linkboard.board.pagination :as pagination]
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
        page (pagination/get-page-param request)
        links-query {:select [:l.*]
                     :from [[:link :l]]
                     :join [[:board :b] [:= :l.board-id :b.id]]
                     :where [:and
                             [:= :b.user-id (:id user)]
                             [:= :b.id (:id path)]]
                     :order-by [[:l.created-at :desc]]}
        links (->> (pagination/add-pagination links-query page)
                   (db/exec! db))
        link-count (->> {:select [[[:count :id] :link-count]]
                         :from [:link]
                         :where [:and
                                 [:= :user-id (:id user)]
                                 [:= :board-id (:id path)]]}
                        (db/exec-one! db)
                        :link-count)
        has-more? (pagination/has-more-pages? link-count page)
        route (str "/boards/" (:id path))
        request* (assoc request :board-id (:id board))]

    (cond
      (not (c/hx-request? request))
      ; Full page response
      (->> (views/board-view request* {:board board
                                       :links links
                                       :link-count link-count
                                       :has-more? has-more?
                                       :route route
                                       :page page})
           (c/body request*)
           (c/base)
           (ext/render-html))

      (pagination/pagination-request? request)
      ; Pagination response - just links + trigger fragment
      (->> (views/board-pagination-view request* {:links links
                                                  :has-more? has-more?
                                                  :route route
                                                  :page page})
           (ext/render-html))

      :else
      ; Standard HTMX page response
      (->> (views/board-view request* {:board board
                                       :links links
                                       :link-count link-count
                                       :has-more? has-more?
                                       :route route
                                       :page page})
           (c/body request*)
           (ext/render-html)))))

(defn all-links-handler
  [{{:keys [db]} :context
    :keys [session]
    :as request}]
  (let [user (q/get-user-by-session-id db (:session-id session))
        page (pagination/get-page-param request)
        links-query {:select [:l.* [:b.title :board-title] [:b.id :board-id]]
                     :from [[:link :l]]
                     :left-join [[:board :b] [:= :l.board-id :b.id]]
                     :where [:= :l.user-id (:id user)]
                     :order-by [[:l.created-at :desc]]}
        links (->> (pagination/add-pagination links-query page)
                   (db/exec! db))
        link-count (->> {:select [[[:count :id] :link-count]]
                         :from [:link]
                         :where [:= :user-id (:id user)]}
                        (db/exec-one! db)
                        :link-count)
        has-more? (pagination/has-more-pages? link-count page)
        route "/links"]

    (cond
      (not (c/hx-request? request))
      ; Full page response
      (->> (views/all-links-view request {:links links
                                          :link-count link-count
                                          :has-more? has-more?
                                          :route route
                                          :page page})
           (c/body request)
           (c/base)
           (ext/render-html))

      (pagination/pagination-request? request)
      ; Pagination response - just links + trigger fragment
      (->> (views/all-links-pagination-view request {:links links
                                                     :has-more? has-more?
                                                     :route route
                                                     :page page})
           (ext/render-html))

      :else
      ; Standard HTMX page response
      (->> (views/all-links-view request {:links links
                                          :link-count link-count
                                          :has-more? has-more?
                                          :route route
                                          :page page})
           (c/body request)
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
          (response/header "HX-Refresh" "true")
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
