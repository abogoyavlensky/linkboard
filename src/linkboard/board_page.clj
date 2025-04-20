(ns linkboard.board-page
  (:require [linkboard.core.db :as db]
            [linkboard.queries :as q]
            [linkboard.routes :as-alias r]
            [linkboard.ui.components :as c]
            [linkboard.ui.icons :as icons]
            [reitit-extras.core :as reitit-extras]
            [ring.util.response :as response]))

; TODO: change to authenticated user
(def USER_ID 1)

(defn- link-list-item
  [{:keys [router link board]}]
  [:div.link-item {:class ["w-full" "bg-white" "rounded-xl" "mb-2" "p-4" "flex"
                           "items-center" "justify-between" "shadow-xs"]}
   [:a {:class ["flex" "items-center" "gap-3"]
        :href (:url link)
        :target "_blank"}
    ; TODO: try to fetch actual favicon from the site by link
    icons/bookmark
    [:div
     [:span {:class ["text-l" "truncate" "w-full" "sm:w-48" "lg:w-96"]} (:title link)]
     [:p {:class ["text-gray-400" "truncate" "w-full" "sm:w-48" "lg:w-96"]} (:url link)]]]
   [:div {:class ["flex" "items-center" "gap-2"]}
    (icons/edit)
    (c/modal
      {:open-btn-text icons/bin
       :title "Delete link"
       :submit-btn-title "Confirm"
       :form-fields [:div
                     [:p {:class ["text-md text-gray-600" "mb-2"]}
                      "Are you sure you want to delete following link?"]
                     [:b {:class ["text-gray-900" "font-semibold" "line-clamp-3"]}
                      (or (:title link) (:url link))]]
       :form-attrs {:hx-delete (reitit-extras/get-route
                                 router
                                 ::r/link-details
                                 {:path {:id (:id board)
                                         :link-id (:id link)}})
                    :hx-headers (reitit-extras/csrf-token-json)
                    :hx-target "closest .link-item"
                    :hx-swap "outerHTML"}})]])

(defn- board-view
  [router {:keys [board links]}]
  [:div {:class ["flex-1" "px-4"]}
   ; Title, back button and add link button
   [:div {:class ["flex" "justify-between" "items-center" "mb-4"]}
    [:div {:class ["flex" "items-center" "gap-2"]}
     [:a {:class ["text-blue-500" "hover:text-blue-600"]
          :hx-get (reitit-extras/get-route router ::r/home-page)
          :hx-target "#content"
          :hx-push-url "true"}
      icons/chevron-left]
     [:h2 {:class ["text-2xl" "font-bold"]} (:title board)]]
    [:div {:class ["flex" "items-center" "gap-2"]}
     ;(components/button {:content [:div {:class ["flex" "items-center" "gap-1"]} icons/plus-circle "Add link"]})
     (c/modal
       {:open-btn-text (c/button {:content [:div {:class ["flex" "items-center" "gap-1"]} icons/plus-circle "Add link"]})
        :title "Add link"
        :form-attrs {:hx-post (reitit-extras/get-route router ::r/board-details-links {:path {:id (:id board)}})
                     :hx-target "#content"}
        :form-fields (list
                       [:input
                        {:class ["flex" "w-full" "h-10" "px-3" "py-2" "text-sm"
                                 "bg-white" "border" "rounded-md" "border-neutral-300"
                                 "ring-offset-background" "placeholder:text-neutral-500"
                                 "focus:border-neutral-300" "focus:outline-hidden"
                                 "focus:ring-2" "focus:ring-offset-2" "focus:ring-neutral-400"
                                 "disabled:cursor-not-allowed" "disabled:opacity-50"]
                         :type "text"
                         :name "url"
                         :minlength 1
                         :autofocus true
                         :placeholder "Enter link url"}])})]]

   (if (seq links)
     (list
       (c/search-bar)
       ; Links
       [:div {:class ["flex-1"]}
        (for [link links]
          (link-list-item {:router router
                           :link link
                           :board board}))])
     ; Empty state
     [:div {:class ["text-center" "mx-auto" "mt-16"]}
      [:h2 {:class ["text-2xl" "font-semibold" "text-gray-900" "mb-3"]} "No bookmarks yet"]
      [:p {:class ["text-gray-600" "mb-8"]} "Start building your collection by adding your first link"]])])

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
        page-view (board-view router {:board board
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
  (let [board-id (-> path-params :id parse-long)]
    (->> {:insert-into :link
          :values [{:url (:url form)
                    :board-id board-id}]}
         (db/exec-one! db))
    ; Render board content
    (board-handler (assoc-in request [:parameters :path] {:id board-id}))))

(defn delete-link-handler
  [{:keys [path-params context]}]
  (let [board-id (-> path-params :id parse-long)]
    ; TODO: fetch board by user, validate if it's owened by user
    (q/delete-link! (:db context) {:link-id (-> path-params :link-id parse-long)
                                   :board-id board-id})
    (response/response nil)))
