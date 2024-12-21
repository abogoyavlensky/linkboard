(ns linkboard.board-page
  (:require [linkboard.components :as components]
            [linkboard.db :as db]
            [linkboard.icons :as icons]
            [linkboard.utils.server :as server-utils]))

; TODO: change to authenticated user
(def USER_ID 1)

(defn- board-view
  [{:keys [board links]}]
  [:div.flex-1.px-4
   ; Title, back button and add link button
   [:div.flex.justify-between.items-center.mb-4
    [:div.flex.items-center.gap-2
     [:a.text-blue-500.hover:text-blue-600
      {:hx-get "/"
       :hx-target "#content"
       :hx-push-url "true"}
      icons/chevron-left]
     [:h2.text-2xl.font-bold (:title board)]
     [:a {:href "#"} (icons/edit {:color "text-blue-500"})]]
    [:div.flex.items-center.gap-2
     [:a {:href "#"} icons/open-all]
     (components/button {:text [:div.flex.items-center.gap-1 icons/plus "Add link"]})]]

   ; Search bar
   [:div.pb-4
    [:div.bg-gray-200.rounded-lg.flex.items-center.px-4.py-2
     [:div.mr-2 icons/search]
     [:input.bg-transparent.flex-1.outline-none.text-gray-700 {:type "text"
                                                               :placeholder "Search"}]]]

   ; Links
   [:div.flex-1
    (for [link links]
      [:a.w-full.bg-white.rounded-xl.mb-4.p-4.flex.items-center.justify-between.shadow-sm
       {:href (:url link)
        :target "_blank"}
       [:div.flex.items-center.gap-3
        ; TODO: try to fetch actual icon from the link
        icons/link
        [:div
         [:span.text-l.truncate.w-full.sm:w-48.lg:w-96 (:title link)]
         [:p.text-gray-400.truncate.w-full.sm:w-48.lg:w-96 (:url link)]]]
       [:div.flex.items-center.gap-2
        (icons/edit)
        icons/bin]])]])

(defn board-handler
  {:malli/schema [:=> [:cat :map] :map]}
  [{{:keys [db]} :context
    {:keys [path]} :parameters
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
                            [:= :b.id (:id path)]]}
                (db/exec! db))]
    (cond-> (board-view {:board board
                         :links links})
      (not (components/hx-request? request)) components/base
      true server-utils/render-html)))
