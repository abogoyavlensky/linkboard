(ns linkboard.board.handlers
  (:require [linkboard.board.views :as views]
            [linkboard.core.db :as db]
            [linkboard.queries :as q]
            [linkboard.routes :as-alias r]
            [linkboard.ui.components :as c]
            [linkboard.utils.url :as url]
            [reitit-extras.core :as reitit-extras]
            [ring.util.response :as response]))

; TODO: change to authenticated user
(def USER_ID 1)

(defn board-handler
  {:malli/schema [:=> [:cat :map] :map]}
  [{{:keys [db]} :context
    {:keys [path]} :parameters
    router :reitit.core/router
    :as request}]
  (let [board (->> {:select [:*]
                    :from [:board]
                    :where [:and
                            [:= :id (:id path)]
                            [:= :user-id USER_ID]]}
                   (db/exec-one! db))
        ; TODO: add pagination
        links (->> {:select [:l.*]
                    :from [[:link :l]]
                    :join [[:board :b] [:= :l.board-id :b.id]]
                    :where [:and
                            [:= :b.user-id USER_ID]
                            [:= :b.id (:id path)]]
                    :order-by [[:l.created-at :desc]]}
                   (db/exec! db))
        page-view (views/board-view router {:board board
                                            :links links})]

    (if (c/hx-request? request)
      (reitit-extras/render-html page-view)
      (->> page-view
           (c/base)
           (reitit-extras/render-html)))))

(defn add-link-handler
  {:malli/schema [:=> [:cat :map] :map]}
  [{{:keys [db]} :context
    {:keys [form]} :parameters
    :keys [path-params]
    :as request}]
  ; TODO: add validation for url!
  ; Add a link to board
  (let [board-id (-> path-params :id parse-long)
        url (:url form)
        ; Fetch metadata for the URL
        metadata (url/fetch-page-metadata url)]
    (->> {:insert-into :link
          :values [{:url url
                    :title (:title metadata)
                    :icon (:icon metadata)
                    :board-id board-id}]}
         (db/exec-one! db))
    ; Render board content
    (board-handler (assoc-in request [:parameters :path] {:id board-id}))))

(defn update-link-handler
  [{{:keys [db]} :context
    {:keys [form]} :parameters
    :keys [path-params]
    :as request}]
  (let [board-id (-> path-params :id parse-long)
        link-id (-> path-params :link-id parse-long)
        title (:title form)
        url (:url form)]
    ; Update link in the database
    (->> {:update :link
          :set {:title title
                :url url}
          :where [:and
                  [:= :id link-id]
                  [:= :board-id board-id]]}
         (db/exec-one! db))
    ; Render updated board content
    (board-handler (assoc-in request [:parameters :path] {:id board-id}))))

(defn update-board-handler
  [{{:keys [db]} :context
    {:keys [form]} :parameters
    :keys [path-params]
    :as request}]
  (let [board-id (-> path-params :id parse-long)
        title (:title form)]
    ; Update board in the database
    (->> {:update :board
          :set {:title title}
          :where [:and
                  [:= :id board-id]
                  [:= :user-id USER_ID]]}
         (db/exec-one! db))
    ; Render updated board content
    (board-handler (assoc-in request [:parameters :path] {:id board-id}))))

(defn delete-board-handler
  [{{:keys [db]} :context
    :keys [path-params]}]
  (let [board-id (-> path-params :id parse-long)]
    ; Delete board (this will cascade delete all links in the board)
    (->> {:delete-from :board
          :where [:and
                  [:= :id board-id]
                  [:= :user-id USER_ID]]}
         (db/exec-one! db))
    ; Redirect to home page
    (-> (response/response nil)
        (response/header "HX-Redirect" "/"))))

(defn delete-link-handler
  [{:keys [path-params context]}]
  (let [board-id (-> path-params :id parse-long)]
    ; TODO: fetch board by user, validate if it's owened by user
    (q/delete-link! (:db context) {:link-id (-> path-params :link-id parse-long)
                                   :board-id board-id})
    (response/response nil)))
