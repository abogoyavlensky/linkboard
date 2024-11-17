(ns linkboard.home
  (:require [hiccup2.core :as h]
            [linkboard.utils.server :as server-utils]))

(defn base
  [content]
  (list
    (h/raw "<!DOCTYPE html>")
    [:html
     [:head
      [:meta {:charset "UTF-8"}
       [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]]
      [:script {:type "text/javascript" :src "/assets/js/tailwindcss.3.4.15.min.js"}]
      ; TODO add favicon
      ;[:link {:rel "icon" :href "/assets/images/favicon.png"}]
      [:title "Linkboard"]]
     [:body
      content
      [:script {:type "text/javascript" :src "/assets/js/htmx.2.0.3.min.js"}]
      [:script {:type "text/javascript" :src "/assets/js/alpinejs.3.14.3.min.js" :defer true}]]]))


(defn home-view
  []
  (base
    [:h1.text-xl.font-bold "Hello world!"]))

(defn home-handler
  [_request]
  (server-utils/render-html (home-view)))
