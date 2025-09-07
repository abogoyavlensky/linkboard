(ns linkboard.home-test
  {:clj-kondo/config '{:linters {:private-call {:level :off}}}}
  (:require [bond.james :as bond]
            [clojure.test :refer :all]
            [etaoin.api :as e]
            [etaoin.keys :as k]
            [integrant-extras.tests :as ig-extras]
            [linkboard.board.fetch :as fetch]
            [linkboard.server :as-alias server]
            [linkboard.test-utils :as utils]
            ; adds support for `match?` and `thrown-match?` in `is`
            [matcher-combinators.test]
            [reitit-extras.tests :as ext]))

(use-fixtures :once
  (ig-extras/with-system "config.test.edn"))

(use-fixtures :each
  utils/with-truncated-tables)

(deftest test-empty-home-page
  (let [url (ext/get-server-url (utils/->server))]
    (utils/with-chrome driver
      ; Navigate to home page
      (e/go driver url)
      ; all the elements should be visible on the page
      (e/wait-visible driver {:tag :h1
                              :fn/has-text "Linkboard"})
      (is (e/visible? driver {:fn/has-text "No boards yet"}))
      (is (e/visible? driver {:fn/has-text "All Links"}))
      (is (e/visible? driver {:fn/has-text "Login"}))
      (is (e/visible? driver {:fn/has-text "Register"}))
      (is (e/visible? driver {:fn/has-text "Using temporary session. Register account to keep your data permanently."})))))

(deftest test-create-board-unauth
  (let [url (ext/get-server-url (utils/->server))]
    (testing "no boards created"
      (is (= [] (utils/get-all-boards (utils/->db)))))

    (utils/with-chrome
      driver
      (e/go driver url)
      (e/wait-visible driver {:tag :button
                              :id "create-board-modal-btn"})
      (e/click driver {:tag :button
                       :id "create-board-modal-btn"})
      (e/wait-visible driver {:id "board-form-fields"})
      (e/fill driver {:tag :input
                      :name :title} "My Test Board")
      ; create the board
      (e/click driver {:tag :button
                       :fn/text "Save"})
      ; board is visible on the page
      (e/wait-visible driver {:id "board-1"})
      (e/wait-visible driver {:fn/has-text "My Test Board"})
      (e/wait-invisible driver {:fn/has-text "No boards yet"})
      ; open board page
      (e/click driver {:fn/has-text "My Test Board"})
      (e/wait-visible driver {:fn/has-text "No bookmarks yet"}))

    (testing "board created in db"
      (is (match? [{:created-at string?
                    :favorite 0
                    :id 1
                    :title "My Test Board"
                    :user-id 1}]
                  (utils/get-all-boards (utils/->db)))))))

(deftest test-create-board-unauth-with-enter-key
  (let [url (ext/get-server-url (utils/->server))]
    (testing "no boards created"
      (is (= [] (utils/get-all-boards (utils/->db)))))

    (utils/with-chrome driver
      (e/go driver url)
      (e/wait-visible driver {:tag :button
                              :id "create-board-modal-btn"})
      (e/click driver {:tag :button
                       :id "create-board-modal-btn"})
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

(deftest test-create-boardless-link-from-home-page
  (let [url (ext/get-server-url (utils/->server))]
    (bond/with-stub [[fetch/fetch-url-limited (constantly {:html "<h1></h1>"})]]
      (utils/with-chrome
        driver
        (e/go driver url)
        ; Click Add link button in footer
        (e/wait-visible driver {:fn/has-text "Add link"})
        (e/click driver {:fn/has-text "Add link"})
        ; Wait for modal and fill form
        (e/wait-visible driver {:id "link-form-fields"})
        (e/wait-visible driver {:fn/has-text "Title (optional)"})
        (e/fill driver {:css "#link-form-fields input[name='title']"} "Test Link Title")
        (e/fill driver {:tag :input
                        :name :url} "https://example.com")
        ; Submit form
        (e/wait-visible driver {:id "add-link-submit-btn"
                                :fn/text "Save"})
        (e/click driver {:id "add-link-submit-btn"
                         :fn/text "Save"})
        ; Verify redirect to All Links page
        (e/wait-visible driver {:fn/has-text "All Links"})
        ; Verify link appears in the list
        (e/wait-visible driver {:fn/has-text "Test Link Title"})
        (is (e/visible? driver {:fn/has-text "https://example.com"})))

      (testing "boardless link created in db"
        (is (match? [{:created-at string?
                      :favorite 0
                      :id 1
                      :title "Test Link Title"
                      :url "https://example.com"
                      :icon nil
                      :board-id nil
                      :user-id 1}]
                    (utils/get-all-links (utils/->db))))))))
