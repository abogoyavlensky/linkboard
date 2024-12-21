(ns linkboard.board-page
  (:require [linkboard.components :as components]
            [linkboard.icons :as icons]
            [linkboard.utils.server :as server-utils]))

(defn- board-view
  [{:keys [board]}]
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
    (for [link (:links board)]
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
  [{{:keys [_db]} :context
    :as request}]
  (let [board {:title "My board"
               :link-count 10
               :links [{:title "Link 1"
                        :url "http://example.com"}
                       {:title "Link 2"
                        :url "http://example.com"}]}]
    (cond-> (board-view {:board board})
      (not (components/hx-request? request)) components/base
      true server-utils/render-html)))
