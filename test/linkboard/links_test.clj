(ns linkboard.links-test
  (:require [clojure.test :refer :all]
            ;[hato.client :as http]
            [integrant-extras.tests :as ig-extras]
            ;[linkboard.core.db :as db]
            [linkboard.server :as-alias server]
            [linkboard.test-utils :as utils]))
            ;[reitit-extras.tests :as ext]))

(use-fixtures :once
  (ig-extras/with-system))

(use-fixtures :each
  utils/with-truncated-tables)
