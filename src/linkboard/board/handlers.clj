(ns linkboard.board.handlers
  (:require [clojure.string :as str]
            [linkboard.board.fetch :as fetch]
            [linkboard.board.pagination :as pagination]
            [linkboard.board.views :as views]
            [linkboard.core.db :as db]
            [linkboard.queries :as q]
            [linkboard.routes :as-alias r]
            [linkboard.ui.components :as c]
            [reitit-extras.core :as ext]
            [ring.util.response :as response])
  (:import [java.net URLEncoder]))

(defn- build-route-with-search
  "Build a route URL with optional search query parameter."
  [base-route search-term]
  (if (and search-term (not (str/blank? search-term)))
    (str base-route "?q=" (URLEncoder/encode search-term "UTF-8"))
    base-route))

(defn board-handler
  {:malli/schema [:=> [:cat :map] :map]}
  [{{:keys [db]} :context
    {:keys [path query]} :parameters
    :keys [session]
    router :reitit.core/router
    :as request}]
  (let [user (q/get-user-by-session-id db (:session-id session))
        board (->> {:select [:*]
                    :from [:board]
                    :where [:and
                            [:= :id (:id path)]
                            [:= :user-id (:id user)]]}
                   (db/exec-one! db))
        boards (q/get-user-boards-minimal db (:id user))
        page (pagination/get-page-param request)
        search-term (:q query)
        links-query (q/get-board-links-query (:id user) (:id path) search-term)
        links (->> (pagination/add-pagination links-query page)
                   (db/exec! db)
                   (mapv (fn [v] (update v :favorite #(> % 0)))))
        link-count (if (and search-term (not (str/blank? search-term)))
                     ; Count search results within board using hybrid approach  
                     (->> (dissoc (assoc links-query :select [[[:count :*] :link-count]]) :left-join :order-by)
                          (db/exec-one! db)
                          :link-count)
                     ; Count all links in board (no search)
                     (->> {:select [[[:count :id] :link-count]]
                           :from [:link]
                           :where [:and
                                   [:= :user-id (:id user)]
                                   [:= :board-id (:id path)]]}
                          (db/exec-one! db)
                          :link-count))
        has-more? (pagination/has-more-pages? link-count page)
        route (build-route-with-search (str "/boards/" (:id path)) search-term)
        request* (assoc request :board-id (:id board)
                                :hide-board-input true)]

    (cond
      (not board)
      (-> (c/error-page "404 - Board not found")
          (ext/render-html)
          (response/status 200))

      (not (c/hx-request? request))
      ; Full page response
      (->> (views/board-view request* {:board board
                                       :links links
                                       :link-count link-count
                                       :has-more? has-more?
                                       :route route
                                       :page page
                                       :search-term search-term
                                       :boards boards})
           (c/body request*)
           (c/base)
           (ext/render-html))

      (pagination/pagination-request? request)
      ; Pagination response - just links + trigger fragment
      (->> (views/board-pagination-view request* {:links links
                                                  :has-more? has-more?
                                                  :route route
                                                  :page page
                                                  :search-term search-term
                                                  :boards boards})
           (ext/render-html))

      :else
      ; HTMX search request - just links
      (->> (views/link-list {:links links
                             :has-more? has-more?
                             :route route
                             :page page
                             :router router
                             :request request
                             :boards boards})
           (ext/render-html)))))

(defn all-links-handler
  [{{:keys [db]} :context
    {:keys [query]} :parameters
    :keys [session]
    router :reitit.core/router
    :as request}]
  (let [user (q/get-user-by-session-id db (:session-id session))
        boards (q/get-user-boards-minimal db (:id user))
        page (pagination/get-page-param request)
        search-term (:q query)
        links-query (q/get-all-links-query (:id user) search-term)
        links (->> (pagination/add-pagination links-query page)
                   (db/exec! db)
                   (mapv (fn [v] (update v :favorite #(> % 0)))))
        link-count (if (and search-term (not (str/blank? search-term)))
                     ; Count search results using FTS5
                     (->> (dissoc (assoc links-query :select [[[:count :*] :link-count]]) :left-join :order-by)
                          (db/exec-one! db)
                          :link-count)
                     ; Count all user links (no search)
                     (->> {:select [[[:count :id] :link-count]]
                           :from [:link]
                           :where [:= :user-id (:id user)]}
                          (db/exec-one! db)
                          :link-count))
        has-more? (pagination/has-more-pages? link-count page)
        route (build-route-with-search "/links" search-term)
        ; Add boards data to request for footer modal
        request* (assoc request :boards boards)]

    (cond
      (not (c/hx-request? request))
      ; Full page response
      (->> (views/all-links-view request {:links links
                                          :link-count link-count
                                          :has-more? has-more?
                                          :route route
                                          :page page
                                          :search-term search-term
                                          :boards boards})
           (c/body request*)
           (c/base)
           (ext/render-html))

      (pagination/pagination-request? request)
      ; Pagination response - just links + trigger fragment
      (->> (views/all-links-pagination-view request {:links links
                                                     :has-more? has-more?
                                                     :route route
                                                     :page page
                                                     :search-term search-term
                                                     :boards boards})
           (ext/render-html))

      :else
      ; HTMX search request - just links
      (->> (views/link-list {:links links
                             :has-more? has-more?
                             :show-board? true
                             :route route
                             :page page
                             :router router
                             :request request
                             :boards boards})
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
    (let [user (q/get-user-by-session-id db (:session-id session))
          boards (q/get-user-boards-minimal db (:id user))]
      (-> (views/link-edit-form-fields request {:link form
                                                :boards boards})
          (ext/render-html)
          (response/status 400)))

    :else
    (let [link-id (-> path :link-id)
          title (:title form)
          url (:url form)
          board-id (when (and (:board-id form)
                              (not (str/blank? (str (:board-id form)))))
                     (parse-long (str (:board-id form))))
          user (q/get-user-by-session-id db (:session-id session))
          current-link (q/get-link-by-id-and-user-id db link-id (:id user))
          boards (q/get-user-boards-minimal db (:id user))
          url-changed? (not= (:url current-link) url)
          metadata (when url-changed? (fetch/fetch-page-metadata url))]
      ; Validate board ownership if board-id is provided
      (if (and board-id (not (q/user-owns-board? db {:board-id board-id
                                                     :session-id (:session-id session)})))
        (-> (response/response "Board not found or access denied")
            (response/status 403))
        (let [updated-link (-> (db/exec-one! db {:update :link
                                                 :set {:title title
                                                       :url url
                                                       :icon (if url-changed? (:icon metadata) (:icon current-link))
                                                       :board-id board-id}
                                                 :where [:and
                                                         [:= :id link-id]
                                                         [:= :user-id (:id user)]]
                                                 :returning [:*]})
                               (update :favorite #(> % 0))
                               (assoc :board-title (when board-id
                                                     (->> boards
                                                          (filter #(= (:id %) board-id))
                                                          first
                                                          :title))))]
          (-> (views/link-list-item {:request request
                                     :router router
                                     :link updated-link
                                     :show-board? true
                                     :boards boards})
              (ext/render-html)
              (response/header "HX-Trigger" "showLinkEditToast")
              (response/header "HX-Trigger-After-Swap" "modal-close")))))))

(defn update-board-handler
  [{{:keys [db]} :context
    {:keys [form]} :parameters
    :keys [parameters session errors]
    :as request}]
  (if (seq errors)
    (-> (views/board-edit-form-fields request {:board form})
        (ext/render-html)
        (response/status 400))
    (let [user (q/get-user-by-session-id db (:session-id session))
          board-id (-> parameters :path :id)
          title (:title form)
          board (->> {:update :board
                      :set {:title title}
                      :where [:and
                              [:= :id board-id]
                              [:= :user-id (:id user)]]
                      :returning [:*]}
                     (db/exec-one! db))]
      ; Render updated board content
      (-> (views/board-title board)
          (ext/render-html)
          (response/header "HX-Trigger" "showBoardEditToast")
          (response/header "HX-Trigger-After-Swap" "modal-close")))))

(defn delete-board-handler
  [{{:keys [db]} :context
    :keys [path-params session]}]
  (let [user (q/get-user-by-session-id db (:session-id session))
        board-id (-> path-params :id parse-long)]
    (->> {:delete-from :board
          :where [:and
                  [:= :id board-id]
                  [:= :user-id (:id user)]]}
         (db/exec-one! db))
    (-> (views/deleted-board-message)
        (ext/render-html)
        (response/header "HX-Trigger" "showBoardDeletionToast")
        (response/header "HX-Trigger-After-Swap" "modal-close"))))

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

(defn toggle-link-favorite-handler
  {:malli/schema [:=> [:cat :map] :map]}
  [{{:keys [db]} :context
    {:keys [path]} :parameters
    :keys [session]
    :as _request}]
  (let [link-id (:link-id path)
        user (q/get-user-by-session-id db (:session-id session))]
    (if (q/user-owns-link? db {:link-id link-id
                               :session-id (:session-id session)})
      (let [updated-link (q/toggle-link-favorite! db {:link-id link-id
                                                      :user-id (:id user)})]
        (-> (ext/render-html (views/favorite-link-icon updated-link))
            (response/header "HX-Trigger" (if (:favorite updated-link)
                                            "showLinkFavoriteAddedToast"
                                            "showLinkFavoriteRemovedToast"))))
      (response/status 403))))
