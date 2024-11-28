(ns linkboard.home
  (:require [linkboard.components :as components]
            [linkboard.utils.server :as server-utils]))

(def sidebar
  [:div.flex.h-screen.flex-col.border-e.bg-white.max-w-xs
   [:div.inset-x-0.bottom-0.border-b.border-gray-100
    [:a.flex.items-center.gap-2.bg-white.p-4.hover:bg-gray-50 {:href "#"}
     [:img.size-10.rounded-full.object-cover {:alt "" :src "https://images.unsplash.com/photo-1600486913747-55e5470d6f40?ixlib=rb-1.2.1&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8&auto=format&fit=crop&w=1770&q=80"}]
     [:div
      [:p.text-xs
       [:strong.block.font-medium "Eric Frusciante"]]]]]
   [:div.px-4.py-6
    [:div.flex.justify-between.mb-8
     [:span.grid.h-10.w-32.place-content-center.rounded-lg.bg-gray-100.text-xs.text-gray-600 "All"]
     [:span.grid.h-10.w-32.place-content-center.rounded-lg.bg-gray-100.text-xs.text-gray-600 "Unsorted"]]
    [:h3.text-xs.text-gray-400 "Boards"]
    [:ul.mt-2.space-y-1
     [:li
      [:a.block.rounded-lg.bg-gray-100.px-4.py-2.text-sm.font-medium.text-gray-700 {:href "#"} "General"]]]]])


(defn home-view
  {:malli/schema [:=> [:cat] [:sequential :any]]}
  []
  (components/base
    sidebar))

(defn home-handler
  {:malli/schema [:=> [:cat :map] :map]}
  [_request]
  (server-utils/render-html (home-view)))
