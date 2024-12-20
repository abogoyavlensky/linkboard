(ns linkboard.board-page
  (:require [linkboard.components :as components]
            [linkboard.utils.server :as server-utils]))

(defn- board-view
  [{:keys [board]}]
  [:div.flex-1.px-4
   [:div
    [:div.flex.justify-between
     [:h2.text-2xl.font-bold.mb-4 (:title board)]
     [:div
      [:button.text-blue-500 "Add link"]]]]
   [:div.flex-1
    ; TODO: replace with list-item
    (for [link (:links board)]
      [:a.w-full.bg-white.rounded-xl.mb-4.p-4.flex.items-center.justify-between.shadow-sm
       {:href (:url link)
        :target "_blank"}
       [:div.flex.items-center.gap-3
        [:svg.w-6.h-6.text-blue-500 {:viewBox "0 0 24 24"
                                     :fill "none"
                                     :stroke "currentColor"}
         [:path {:d "M12 2v20M2 12h20"
                 :stroke-width "2"}]]
        [:div
         [:span.text-l.truncate.w-full.sm:w-48.lg:w-96 (:title link)]
         [:p.text-gray-400.truncate.w-full.sm:w-48.lg:w-96 (:url link)]]]
       [:div.flex.items-center.gap-2
        [:svg.w-5.h-5.text-gray-400.rotate-180 {:viewBox "0 0 24 24"
                                                :fill "none"
                                                :stroke "currentColor"}
         [:path {:d "M15 18l-6-6 6-6"
                 :stroke-width "2"}]]]])]])

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
