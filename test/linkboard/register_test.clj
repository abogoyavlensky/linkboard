(ns linkboard.register-test
  {:clj-kondo/config '{:linters {:private-call {:level :off}}}}
  (:require [clojure.test :refer :all]
            [etaoin.api :as e]
            [integrant-extras.tests :as ig-extras]
            [linkboard.server :as-alias server]
            [linkboard.test-utils :as utils]
            [matcher-combinators.test] ; support for `match?/thrown-match?` in `is`
            [reitit-extras.tests :as ext]))

(use-fixtures :once
  (ig-extras/with-system "config.test.edn"))

(use-fixtures :each
  utils/with-truncated-tables)

(deftest test-create-account
  (let [url (ext/get-server-url (utils/->server))]
    (testing "no users created initially"
      (is (= [] (utils/get-all-users (utils/->db)))))

    (utils/with-chrome driver
      (e/go driver url)
      ; Verify initial state shows temporary session
      (e/wait-visible driver {:fn/has-text "Using temporary session. Register account to keep your data permanently."})
      (e/wait-visible driver {:fn/has-text "Register"})
      ; Click Register button
      (e/click driver {:fn/has-text "Register"})
      ; Wait for modal to appear with account number
      (e/wait-visible driver {:fn/has-text "Your Account number"})
      ; The account number should be auto-generated and visible
      (is (e/visible? driver {:css "[x-text='accountId']"}))
      ; Click Create Account button
      (e/wait-visible driver {:id "create-account-submit-btn"
                              :tag :button
                              :fn/text "Create Account"})
      (e/click driver {:id "create-account-submit-btn"
                       :tag :button
                       :fn/text "Create Account"})
      ; Should redirect to home page and no longer show temporary session message
      (e/wait-visible driver {:tag :h1
                              :fn/has-text "Linkboard"})
      ;(e/wait 5)
      (e/wait-invisible driver {:fn/has-text "Using temporary session. Register account to keep your data permanently."})
      ; Should show login option instead of register
      (is (e/invisible? driver {:fn/has-text "Login"}))
      (is (e/invisible? driver {:fn/has-text "Register"}))
      (is (e/visible? driver {:fn/has-text "Account"})))

    (testing "user created in database"
      (let [users (utils/get-all-users (utils/->db))]
        (is (= 1 (count users)))
        (is (some? (:account-number (first users))))
        (is (some? (:password (first users))))
        (is (some? (:session-id (first users))))))))
