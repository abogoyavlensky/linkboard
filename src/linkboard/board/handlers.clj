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
        page-view (views/board-view request {:board board
                                             :links links})]

    (if (c/hx-request? request)
      (ext/render-html page-view)
      (->> page-view
           (c/base request)
           (ext/render-html)))))

(defn add-link-handler
  {:malli/schema [:=> [:cat :map] :map]}
  [{{:keys [db]} :context
    {:keys [form]} :parameters
    :keys [errors parameters session]
    router :reitit.core/router
    :as request}]
  (cond
    (not (q/user-owns-board? db {:board-id (get-in parameters [:path :id])
                                 :session-id (:session-id session)}))
    (response/status 403)

    (seq errors)
    (-> (views/link-form-fields request)
        (ext/render-html))

    :else
    (let [board-id (get-in parameters [:path :id])
          board-path (ext/get-route router ::r/board-details {:path {:id board-id}})
          url (:url form)
          metadata (fetch/fetch-page-metadata url)]
      (->> {:insert-into :link
            :values [{:url url
                      :title (:title metadata)
                      :icon (:icon metadata)
                      :board-id board-id}]}
           (db/exec-one! db))
      (-> (response/response [:div])
          (response/header "HX-Redirect" board-path)))))

(defn update-link-handler
  [{{:keys [db]} :context
    {:keys [form path]} :parameters
    :keys [session errors]
    router :reitit.core/router
    :as request}]
  (cond
    (not (q/user-owns-board? db {:board-id (-> path :id parse-long)
                                 :session-id (:session-id session)}))
    (-> (response/response "Board not found or access denied")
        (response/status 403))

    (seq errors)
    (-> (views/link-form-fields request)
        (ext/render-html))

    :else
    (let [board-id (-> path :id parse-long)
          board-path (ext/get-route router ::r/board-details {:path {:id board-id}})
          link-id (-> path :link-id parse-long)
          title (:title form)
          url (:url form)
          metadata (fetch/fetch-page-metadata url)]
      ; Update link in the database
      (->> {:update :link
            :set {:title title
                  :url url
                  :icon (:icon metadata)}
            :where [:and
                    [:= :id link-id]
                    [:= :board-id board-id]]}
           (db/exec-one! db))
      (-> (response/response [:div])
          (response/header "HX-Redirect" board-path)))))

(defn update-board-handler
  [{{:keys [db]} :context
    {:keys [form]} :parameters
    :keys [path-params session]
    :as request}]
  (let [user (q/get-user-by-session-id db (:session-id session))
        board-id (-> path-params :id parse-long)
        title (:title form)]
    ; Update board in the database
    (->> {:update :board
          :set {:title title}
          :where [:and
                  [:= :id board-id]
                  [:= :user-id (:id user)]]}
         (db/exec-one! db))
    ; Render updated board content
    (board-handler (assoc-in request [:parameters :path] {:id board-id}))))

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
        (response/header "HX-Redirect" "/"))))

(defn delete-link-handler
  [{:keys [path-params context session]}]
  (let [board-id (-> path-params :id parse-long)]
    (cond
      (not (q/user-owns-board? (:db context) {:board-id board-id
                                              :session-id (:session-id session)}))
      (-> (response/response "Board not found or access denied")
          (response/status 403))

      :else
      (do
        (q/delete-link! (:db context) {:link-id (-> path-params :link-id parse-long)
                                       :board-id board-id})
        (response/response nil)))))
