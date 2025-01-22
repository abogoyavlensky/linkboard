(ns linkboard.home-test
  (:require [clojure.test :refer :all]
            [etaoin.api :as etaoin]
            [linkboard.db :as db]
            [linkboard.server :as-alias server]
            [linkboard.test-utils :as test-utils]
            [linkboard.webdriver :as-alias webdriver]))

(use-fixtures :each
  test-utils/with-system)

(deftest test-home-page-list-boards-ok
  (let [db (::db/db test-utils/*test-system*)
        driver (get-in test-utils/*test-system* [::webdriver/webdriver :driver])
        server (::server/server test-utils/*test-system*)
        url (test-utils/get-server-url-inside-testcontainer server)]

    (->> {:insert-into :user
          :values [{:sync_code "test-sync-code"}]}
      (db/exec-one! db))

    (etaoin/go driver url)
    (etaoin/refresh driver)
    (etaoin/wait-visible driver {:tag :h1
                                 :fn/has-text "Linkboard"}
      {:timeout 5})

    (is (= 2 (count (db/exec! db {:select [:*]
                                  :from [:user]}))))))
