(ns linkboard.home
  (:require [linkboard.components :as components]
            [linkboard.utils.server :as server-utils]))

(defn home-view
  []
  (components/base
    [:h1.text-xl.font-bold "Hello world!"]))

(defn home-handler
  [_request]
  (server-utils/render-html (home-view)))
