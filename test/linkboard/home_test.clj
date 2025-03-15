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

(deftest test-home-page-loads-correctly
  (testing "Home page loads and displays correctly"
    (let [db (::db/db ig-extras/*test-system*)
          driver (get-in ig-extras/*test-system* [::webdriver/webdriver :driver])
          server (::server/server ig-extras/*test-system*)
          url (reitit-extras/get-server-url server :container)]

      ; Create test user
      (db/exec-one! db {:insert-into :user
                        :values [{:sync_code "test-sync-code"}]})

      ; Create test boards
      (db/exec-one! db {:insert-into :board
                        :values [{:title "Test Board 1"
                                  :user_id 1}]})

      ; Navigate to home page
      (etaoin/go driver url)
      (etaoin/wait-visible driver {:tag :h1
                                   :fn/has-text "Linkboard"}
        {:timeout 5})

      ; Verify page elements
      (is (etaoin/visible? driver {:tag :h1
                                   :fn/has-text "Linkboard"}))
      (is (etaoin/visible? driver {:tag :h2
                                   :fn/has-text "MY BOARDS"}))

      ; Verify board is displayed
      (is (etaoin/visible? driver {:tag :span
                                   :fn/has-text "Test Board 1"}))

      ; Verify database state
      (is (= 1 (count (db/exec! db {:select [:*]
                                    :from [:user]}))))
      (is (= 1 (count (db/exec! db {:select [:*]
                                    :from [:board]})))))))

(deftest test-index-page-loads-correctly
  (testing "Index page loads and displays correctly"
    (let [driver (get-in ig-extras/*test-system* [::webdriver/webdriver :driver])
          server (::server/server ig-extras/*test-system*)
          url (reitit-extras/get-server-url server :container)]

      ; Navigate to home page
      (etaoin/go driver (str url "/index"))
      (etaoin/wait-visible driver {:tag :span
                                   :fn/has-text "Clojure Stack Lite"}
        {:timeout 5})

      ; Verify page elements
      (is (etaoin/visible? driver {:tag :span
                                   :fn/has-text "Clojure Stack Lite"}))
      (is (etaoin/visible? driver {:tag :a
                                   :fn/has-text "Get Started"})))))
