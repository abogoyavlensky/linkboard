(ns linkboard.links-test
  (:require [bond.james :as bond]
            [clj-http.client :as http]
            [clojure.test :refer :all]
            [integrant-extras.tests :as ig-extras]
            [linkboard.board.fetch :as fetch]
            [linkboard.core.db :as db]
            [linkboard.queries :as q]
            [linkboard.server :as-alias server]
            [linkboard.test-utils :as utils]
            [matcher-combinators.test]
            [reitit-extras.tests :as ext]))

(use-fixtures :once
  (ig-extras/with-system "config.test.edn"))

(use-fixtures :each
  utils/with-truncated-tables)

(def SESSION-ID "test-session-123")

(defn- create-test-user-and-link!
  "Create a test user and link in the database. Returns the created link."
  [db]
  (let [user (q/create-user-with-session! db SESSION-ID)
        link (db/exec-one! db {:insert-into :link
                               :values [{:title "Test Link"
                                         :url "https://example.com"
                                         :icon "https://example.com/icon.png"
                                         :user-id (:id user)}]
                               :returning [:*]})]
    {:link (update link :favorite #(> % 0))
     :user user}))

(deftest test-link-update-skips-metadata-fetch-when-url-unchanged
  (let [db (utils/->db)
        base-url (ext/get-server-url (utils/->server))
        {:keys [link user]} (create-test-user-and-link! db)]
    (bond/with-spy [fetch/fetch-page-metadata]
      (http/put (str base-url "/links/" (:id link))
                {:cookies (ext/session-cookies
                            {ext/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN
                             :session-id SESSION-ID}
                            utils/TEST-SECRET-KEY)
                 :form-params {ext/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                               :title "Updated Title"
                       ; same url
                               :url "https://example.com"}})

      ; Verify fetch-page-metadata was NOT called
      (is (= 0 (-> fetch/fetch-page-metadata bond/calls count)))
      (is (match? {:description nil
                   :user-id (:id user)
                   :icon "https://example.com/icon.png"
                   :title "Updated Title"
                   :board-id nil
                   :id (:id link)
                   :url "https://example.com"
                   :created-at string?
                   :favorite 0}
                  (utils/get-link (utils/->db) {:id (:id link)}))))))

(deftest test-link-update-fetches-metadata-when-url-changed
  (let [db (utils/->db)
        base-url (ext/get-server-url (utils/->server))
        {:keys [link user]} (create-test-user-and-link! db)]
    (bond/with-stub [[fetch/fetch-page-metadata (constantly {:html "<h1></h1>"})]]
      (http/put (str base-url "/links/" (:id link))
                {:cookies (ext/session-cookies
                            {ext/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN
                             :session-id SESSION-ID}
                            utils/TEST-SECRET-KEY)
                 :form-params {ext/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                               :title "Updated Title"
                       ; different url
                               :url "https://newsite.com"}})

      ; Verify fetch-page-metadata was called exactly once
      (is (= 1 (-> fetch/fetch-page-metadata bond/calls count)))
      ; Verify it was called with the new URL
      (is (= "https://newsite.com"
             (-> fetch/fetch-page-metadata bond/calls first :args first)))
      ; Verify the link was updated with new metadata
      (is (match? {:description nil
                   :user-id (:id user)
                   :icon nil
                   :title "Updated Title"
                   :board-id nil
                   :id (:id link)
                   :url "https://newsite.com"
                   :created-at string?
                   :favorite 0}
                  (utils/get-link (utils/->db) {:id (:id link)}))))))
