(ns linkboard.utils
  (:require [reitit-extras.core :as ext]
            [lambdaisland.uri :as uri]
            [linkboard.routes :as-alias r]))

(defn back-url
  [{router :reitit.core/router
    :as request}]
  (let [referer (-> request (get-in [:headers "referer"]) uri/uri :path)
        all-links-url (ext/route router ::r/links)]
    (if (= referer all-links-url)
      all-links-url
      (ext/route router ::r/home-page))))
