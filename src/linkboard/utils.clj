(ns linkboard.utils
  (:require [linkboard.routes :as-alias r]
            [reitit-extras.core :as ext]))

(defn back-url
  [{router :reitit.core/router}]
  (ext/route router ::r/home-page))
