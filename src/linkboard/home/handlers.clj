(ns linkboard.home.handlers
  (:require [linkboard.core.db :as db]
            [linkboard.home.views :as views]
            [linkboard.routes :as-alias r]
            [linkboard.ui.components :as c]
            [ring.util.response :as response]
            [reitit-extras.core :as reitit-extras]))

; TODO: change to authenticated user
(def USER-ID 1)

(defn home-handler
  {:malli/schema [:=> [:cat :map] :map]}
  [{{:keys [db]} :context
    router :reitit.core/router
    :keys [session]
    :as request}]
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
                                             :all-links-count all-links-count
                                             :session session})]
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

(defn update-sync-code-handler
  {:malli/schema [:=> [:cat :map] :map]}
  [{{:keys [form]} :parameters}]
  (-> (reitit-extras/render-html [:div])
    (assoc :session {:sync-code (:sync-code form)})
    (response/header "HX-Refresh" "true")))
