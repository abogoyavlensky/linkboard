(ns linkboard.components
  (:require [hiccup2.core :as h]
            [linkboard.icons :as icons]
            [ring.middleware.anti-forgery :as anti-forgery]))

(def ^:const PROJECT-GITHUB-LINK "https://github.com/abogoyavlensky/linkboard")

(defn hx-request?
  [{:keys [headers]}]
  (= "true" (get headers "hx-request")))

(defn button
  [{:keys [content]}]
  [:button.inline-flex.items-center.px-4.py-2.bg-blue-600.text-white.rounded-lg.hover:bg-blue-700.transition-colors
   {:type "button"}
   content])

(defn base
  "Base component for html page."
  {:malli/schema [:=> [:cat [:vector :any]] [:sequential :any]]}
  [content]
  (list
    (h/raw "<!DOCTYPE html>")
    [:html
     [:head
      [:meta {:charset "UTF-8"}
       [:meta {:name "viewport"
               :content "width=device-width, initial-scale=1.0"}]]
      [:link {:type "text/css"
              :href "/assets/css/output.css"
              :rel "stylesheet"}]
      [:link {:rel "icon"
              :href "/assets/images/favicon128x128.ico"}]
      [:title "Linkboard"]]
     [:body.bg-gray-50
      [:div.h-screen.flex.flex-col.max-w-4xl.mx-auto
       [:div.px-4.pt-2.pb-4.mb-4.flex.justify-between.items-center
        [:div
         [:a
          {:hx-get "/"
           :hx-target "#content"
           :hx-push-url "true"}
          [:h1.text-3xl.font-bold.cursor-pointer "Linkboard"]]
         [:div.text-gray-400.flex.items-center.gap-2
          [:p "Personal bookmark manager"]
          [:a
           {:href PROJECT-GITHUB-LINK
            :target "_blank"}
           icons/github]]]
        [:div.flex.gap-4
         [:a.text-blue-500.text-lg {:href "#"} "Sync"]]]
       [:div
        {:id "content"
         :hx-history-elt true}
        content]]
      [:script {:type "text/javascript"
                :src "/assets/js/htmx.2.0.3.min.js"}]
      [:script {:type "text/javascript"
                :src "/assets/js/alpinejs.3.14.3.min.js"
                :defer true}]]]))

(defn csrf-token
  []
  [:input {:type "hidden"
           :name "__anti-forgery-token"
           :id "__anti-forgery-token"
           :value (force anti-forgery/*anti-forgery-token*)}])

(defn modal
  [{:keys [title open-btn-text]}]
  [:div.relative.w-auto.h-auto
   {:x-data "{ modalOpen: false }"
    :x-on:keydown.escape.window "modalOpen = false"
    ::class "{ 'z-40': modalOpen }"}
   [:button
    {:x-on:click "modalOpen=true"
     :class "focus:ring-neutral-200/60"}
    open-btn-text]
   [:div.fixed.top-0.left-0.flex.items-center.justify-center.w-screen.h-screen
    {:x-show "modalOpen"
     :class "z-[99]"
     :x-cloak "true"}
    [:div.absolute.inset-0.w-full.h-full.bg-white.backdrop-blur-sm.bg-opacity-70
     {:x-show "modalOpen"
      :x-transition:enter "ease-out duration-300"
      :x-transition:enter-start "opacity-0"
      :x-transition:enter-end "opacity-100"
      :x-transition:leave "ease-in duration-300"
      :x-transition:leave-start "opacity-100"
      :x-transition:leave-end "opacity-0"
      :x-on:click "modalOpen=false"}]
    [:form.relative.w-full.py-6.bg-white.border.shadow-lg.px-7.border-neutral-200.max-w-xs.md:max-w-md.rounded-lg
     {:hx-post "/boards"
      :hx-target "#board-list"
      :x-show "modalOpen"
      :x-trap.inert.noscroll "modalOpen"
      :x-transition:enter "ease-out duration-300"
      :x-transition:enter-start "opacity-0 -translate-y-2 sm:scale-95"
      :x-transition:enter-end "opacity-100 translate-y-0 sm:scale-100"
      :x-transition:leave "ease-in duration-200"
      :x-transition:leave-start "opacity-100 translate-y-0 sm:scale-100"
      :x-transition:leave-end "opacity-0 -translate-y-2 sm:scale-95"}
     [:div.flex.items-center.justify-between.pb-3
      [:h3.text-lg.font-semibold title]
      [:button.absolute.top-0.right-0.flex.items-center.justify-center.w-8.h-8.mt-5.mr-5.text-gray-600.rounded-full.hover:text-gray-800.hover:bg-gray-50
       {:x-on:click "modalOpen=false"}
       [:svg.w-5.h-5 {:xmlns "http://www.w3.org/2000/svg"
                      :fill "none"
                      :viewBox "0 0 24 24"
                      :stroke-width "1.5"
                      :stroke "currentColor"}
        [:path {:stroke-linecap "round"
                :stroke-linejoin "round"
                :d "M6 18L18 6M6 6l12 12"}]]]]
     [:div.relative.w-auto.pb-8
      [:div.w-full.max-w-xs.mx-auto
       [:input.flex.w-full.h-10.px-3.py-2.text-sm.bg-white.border.rounded-md.border-neutral-300.ring-offset-background.placeholder:text-neutral-500.focus:border-neutral-300.focus:outline-none.focus:ring-2.focus:ring-offset-2.focus:ring-neutral-400.disabled:cursor-not-allowed.disabled:opacity-50
        {:type "text"
         :name "title"
         :minlength 1
         :autofocus true
         :placeholder "Enter board name"}]
       (csrf-token)]]
     [:div.flex.flex-row.justify-end.space-x-2
      [:button.inline-flex.items-center.justify-center.h-10.px-4.py-2.text-sm.font-medium.transition-colors.border.rounded-md.focus:outline-none.focus:ring-2.focus:ring-neutral-100.focus:ring-offset-2
       {:x-on:click "modalOpen=false"
        :type "button"} "Cancel"]
      [:button.inline-flex.items-center.justify-center.px-4.py-2.bg-blue-600.text-white.rounded-lg.hover:bg-blue-700.transition-colors
       {:x-on:click "modalOpen=false"
        :type "submit"}
       "Save"]]]]])
