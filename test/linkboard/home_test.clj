(ns linkboard.home-test
  (:require [clojure.test :refer :all]
            [etaoin.api :as etaoin]
            [integrant-extras.tests :as ig-extras]
            [linkboard.db :as db]
            [linkboard.server :as-alias server]
            [linkboard.test-utils :as test-utils]
            [linkboard.webdriver :as-alias webdriver]
            [reitit-extras.tests :as reitit-extras]))

(use-fixtures :once
  (ig-extras/with-system "config.e2e.edn"))

(use-fixtures :each
  test-utils/with-truncated-tables)

; TODO: improve test
(deftest test-home-page-list-boards-ok
  (let [db (::db/db ig-extras/*test-system*)
        driver (get-in ig-extras/*test-system* [::webdriver/webdriver :driver])
        server (::server/server ig-extras/*test-system*)
        url (reitit-extras/get-server-url server :container)]

    (->> {:insert-into :user
          :values [{:sync_code "test-sync-code"}]}
      (db/exec-one! db))

    (etaoin/go driver url)
    (etaoin/refresh driver)
    (etaoin/wait-visible driver {:tag :h1
                                 :fn/has-text "Linkboard"}
      {:timeout 5})

    (is (= 1 (count (db/exec! db {:select [:*]
                                  :from [:user]}))))))
