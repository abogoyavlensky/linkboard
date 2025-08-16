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

;(defn- create-user-and-board
;  [db session-id]
;  ; Create test user
;  (let [user (db/exec-one! db {:insert-into :user
;                               :values [{:session-id session-id}]
;                               :returning [:*]})
;        ; Create test board
;        board (db/exec-one! db {:insert-into :board
;                                :values [{:title "Test Board"
;                                          :user-id (:id user)}]
;                                :returning [:*]})]
;    {:user user :board board}))

; TODO: fix tests!

;(deftest test-create-link-without-board
;  (let [base-url (ext/get-server-url (utils/server))
;        session-id "test-session-123"]
;
;    ; Create test user
;    (create-user-and-board (utils/db) session-id)
;
;    ; Make POST request to create link
;    (let [response (http/post (str base-url "/links")
;                              {:form-params {ext/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
;                                             :url "https://example.com"
;                                             :board_id nil}
;                               :cookies (ext/session-cookies
;                                          {ext/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN
;                                           :session-id session-id}
;                                          utils/TEST-SECRET-KEY)
;                               :throw-exceptions false})]
;
;      ; Should redirect successfully
;      (is (= 302 (:status response)))
;      (is (= "/" (get-in response [:headers "HX-Redirect"])))
;
;      ; Verify link was created in database
;      (let [links (db/exec! (utils/db) {:select [:*] :from [:link]})]
;        (is (= 1 (count links)))
;        (let [link (first links)]
;          (is (= "https://example.com" (:url link)))
;          (is (nil? (:board-id link))))))))
;
;(deftest test-create-link-with-board
;  (testing "Creating a link with board_id should succeed when user owns the board"))
;
;
;(deftest test-create-link-with-non-owned-board
;  (testing "Creating a link with board_id user doesn't own should return 403"))
;
;
;(deftest test-create-link-invalid-url
;  (testing "Creating a link with invalid URL should return 400"))
;
;
;(deftest test-create-link-missing-url
;  (testing "Creating a link without URL should return 400"))
