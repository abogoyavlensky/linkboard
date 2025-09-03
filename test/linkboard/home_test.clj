(ns linkboard.home-test
  (:require [clojure.test :refer :all]
            [etaoin.api :as e]
            [etaoin.keys :as k]
            [integrant-extras.tests :as ig-extras]
            [linkboard.server :as-alias server]
            [linkboard.test-utils :as utils]
            ; adds support for `match?` and `thrown-match?` in `is`
            [matcher-combinators.test]
            [reitit-extras.tests :as ext]))

(use-fixtures :once
  (ig-extras/with-system))

(use-fixtures :each
  utils/with-truncated-tables)

(deftest test-empty-home-page
  (let [url (ext/get-server-url (utils/->server))]

    (e/with-chrome-headless
      {:path-driver (utils/driver-path!)}
      driver
      ; Navigate to home page
      (e/go driver url)
      ; all the elements should be visible on the page
      (e/wait-visible driver {:tag :h1
                              :fn/has-text "Linkboard"})
      (e/wait-visible driver {:fn/has-text "No boards yet"})
      (e/wait-visible driver {:fn/has-text "All Links"})
      (e/wait-visible driver {:fn/has-text "Login"})
      (e/wait-visible driver {:fn/has-text "Register"})
      (e/wait-visible driver {:fn/has-text "Using temporary session. Register account to keep your data permanently."}))
    (is (= 1 1))))

(deftest test-create-board-unauthenticated
  (let [url (ext/get-server-url (utils/->server))]
    (testing "no boards creatd"
      (is (= [] (utils/get-all-boards (utils/->db)))))

    (e/with-chrome-headless
      {:path-driver (utils/driver-path!)}
      driver

      (e/go driver url)
      (e/wait-visible driver {:tag :button
                              :id "create-board-btn"})
      (e/click driver {:tag :button
                       :id "create-board-btn"})
      (e/wait-visible driver {:id "board-form-fields"})
      (e/fill driver {:tag :input
                      :name :title} "My Test Board")
      (e/fill driver {:tag :input
                      :name :title} k/enter))

    (testing "board created in db"
      (is (match? [{:created-at string?
                    :favorite 0
                    :id 1
                    :title "My Test Board"
                    :user-id 1}]
                  (utils/get-all-boards (utils/->db)))))))
