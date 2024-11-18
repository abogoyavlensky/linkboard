(ns linkboard.home
  (:require [linkboard.components :as components]
            [linkboard.utils.server :as server-utils]))

(defn home-view
  []
  (components/base
    [:div
     [:h1.text-xl.font-bold "Hello world!"]
     [:p.text-md "Some description"]]))

(defn home-handler
  [_request]
  (server-utils/render-html (home-view)))
