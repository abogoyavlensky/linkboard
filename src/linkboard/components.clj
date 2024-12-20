(ns linkboard.components
  (:require [hiccup2.core :as h]))

(defn hx-request?
  [{:keys [headers]}]
  (= "true" (get headers "hx-request")))

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
      [:div.h-screen.flex.flex-col.max-w-md.md:max-w-4xl.mx-auto
       [:div.px-4.pt-2.pb-4.mb-4.flex.justify-between.items-center
        [:a
         {:href "/"}
         [:h1.text-3xl.font-bold "Linkboard"]]
        [:a.text-blue-500.text-lg {:href "#"} "Sync"]]
       [:div#content content]]
      [:script {:type "text/javascript"
                :src "/assets/js/htmx.2.0.3.min.js"}]
      [:script {:type "text/javascript"
                :src "/assets/js/alpinejs.3.14.3.min.js"
                :defer true}]]]))
