(ns linkboard.home
  (:require [linkboard.utils.server :as server-utils]
            [linkboard.components :as components]))


(defn home-view
  []
  (components/base
    [:h1.text-xl.font-bold "Hello world!"]))

(defn home-handler
  [_request]
  (server-utils/render-html (home-view)))
