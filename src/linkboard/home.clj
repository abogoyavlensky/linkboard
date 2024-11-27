(ns linkboard.home
  (:require [linkboard.components :as components]
            [linkboard.utils.server :as server-utils]))

(defn home-view
  {:malli/schema [:=> [:cat] [:sequential :any]]}
  []
  (components/base
    [:div
     [:h1.text-xl.font-bold "Hello world!?:"]
     [:p.text-md "Some description"]]))

(defn home-handler
  {:malli/schema [:=> [:cat :map] :map]}
  [_request]
  (server-utils/render-html (home-view)))
