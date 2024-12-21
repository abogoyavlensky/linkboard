(ns linkboard.components
  (:require [hiccup2.core :as h]
            [linkboard.icons :as icons]))

(def ^:const PROJECT-GITHUB-LINK "https://github.com/abogoyavlensky/linkboard")

(defn hx-request?
  [{:keys [headers]}]
  (= "true" (get headers "hx-request")))

(defn button
  [{:keys [text]}]
  [:button.inline-flex.items-center.justify-center.px-4.py-2.text-sm.font-medium.tracking-wide.text-blue-500.transition-colors.duration-100.rounded-md.focus:ring-2.focus:ring-offset-2.focus:ring-blue-100.bg-blue-50.hover:text-blue-600.hover:bg-blue-100
   {:type "button"}
   text])

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
         [:div.text-gray-400.truncate.w-full.sm:w-48.lg:w-96.flex.items-center.gap-2
          "Personal bookmark manager"
          [:a
           {:href PROJECT-GITHUB-LINK
            :target "_blank"}
           icons/github]]]
        [:div.flex.gap-4
         [:a.text-blue-500.text-lg {:href "#"} "Sync"]]]
       [:div#content content]]
      [:script {:type "text/javascript"
                :src "/assets/js/htmx.2.0.3.min.js"}]
      [:script {:type "text/javascript"
                :src "/assets/js/alpinejs.3.14.3.min.js"
                :defer true}]]]))
