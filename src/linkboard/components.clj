(ns linkboard.components
  (:require [hiccup2.core :as h]))

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
      content
      [:script {:type "text/javascript"
                :src "/assets/js/htmx.2.0.3.min.js"}]
      [:script {:type "text/javascript"
                :src "/assets/js/alpinejs.3.14.3.min.js"
                :defer true}]]]))
