(ns linkboard.auth-test
  {:clj-kondo/config '{:linters {:private-call {:level :off}}}}
  (:require [clojure.test :refer :all]
            [etaoin.api :as e]
            [integrant-extras.tests :as ig-extras]
            [linkboard.queries :as queries]
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
      ; Wait for Alpine.js to initialize and Register button to be ready
      (e/wait-visible driver {:fn/has-text "Register"} {:timeout 10})
      (Thread/sleep 1000) ; Give Alpine.js time to initialize

      ; Click Register button
      (e/click driver {:fn/has-text "Register"})

      ; Wait for modal to appear with account number
      (e/wait-visible driver
                      {:fn/has-text "Your Account number"}
                      {:timeout 30})
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
      (e/wait-invisible driver {:fn/has-text "Using temporary session. Register account to keep your data permanently."})
      (is (e/invisible? driver {:fn/has-text "Login"}))
      (is (e/invisible? driver {:fn/has-text "Register"}))
      (is (e/visible? driver {:fn/has-text "Account"})))

    (testing "user created in database"
      (let [users (utils/get-all-users (utils/->db))]
        (is (= 1 (count users)))
        (is (some? (:account-number (first users))))
        (is (some? (:password (first users))))
        (is (some? (:session-id (first users))))))))

(deftest test-login-with-existing-account
  (let [url (ext/get-server-url (utils/->server))
        test-account-number "TEST-MTAQ-G6XT-TEST"
        test-session-id "test-session-123"]

    (queries/create-user! (utils/->db) test-session-id test-account-number)

    (utils/with-chrome driver
      (e/go driver url)

      ; Verify initial state - should show temporary session and Login option
      (e/wait-visible driver {:fn/has-text "Using temporary session. Register account to keep your data permanently."})
      (e/wait-visible driver {:fn/has-text "Login"})

      ; Wait for Alpine.js to initialize and login button to be ready
      (e/wait-visible driver {:id "login-header-modal-btn"} {:timeout 10})
      (Thread/sleep 1000) ; Give Alpine.js time to initialize

      ; Click Login button to open modal
      (e/click driver {:id "login-header-modal-btn"})

      ; Wait for modal backdrop/overlay to appear (teleported to body)
      (e/wait-visible driver {:css "div.fixed.inset-0"} {:timeout 15})

      ; Wait for the login form fields container to be present
      (e/wait-visible driver {:id "login-form-fields"} {:timeout 15})

      ; Now wait for the specific modal content
      (e/wait-visible driver {:tag :h3
                              :fn/has-text "Login"} {:timeout 30})
      (e/wait-visible driver {:fn/has-text "Enter your account number"})

      ; Fill in the account number
      (e/fill driver {:css "#login-form-fields input[name='account-number']"} test-account-number)

      ; Submit the login form
      (e/wait-visible driver {:id "login-submit-btn"
                              :tag :button
                              :fn/text "Login"})
      (e/click driver {:id "login-submit-btn"
                       :tag :button
                       :fn/text "Login"})

      ; Verify successful login - should redirect to home page without temporary session message
      (e/wait-visible driver {:tag :h1
                              :fn/has-text "Linkboard"})
      (e/wait-invisible driver {:fn/has-text "Using temporary session. Register account to keep your data permanently."})

      (is (e/invisible? driver {:fn/has-text "Login"}))
      (is (e/invisible? driver {:fn/has-text "Register"}))
      (is (e/visible? driver {:fn/has-text "Account"})))

    (testing "user remains in database"
      (let [users (utils/get-all-users (utils/->db))]
        (is (= 1 (count users)))
        (is (= "TEST-MTAQ" (:account-number (first users))))
        (is (some? (:password (first users))))
        (is (some? (:session-id (first users))))))))
