(ns linkboard.home-test
  (:require [clojure.test :refer :all]
            [etaoin.api :as etaoin]
            [hato.client :as client]
            [linkboard.db :as db]
            [linkboard.webdriver :as-alias webdriver]
            [linkboard.server :as-alias server]
            [linkboard.test-utils :as test-utils]))

(use-fixtures :each
  test-utils/with-system)

(deftest test-home-page-list-boards-ok
  (let [db (::db/db test-utils/*test-system*)
        driver (get-in test-utils/*test-system* [::webdriver/webdriver :driver])
        server (::server/server test-utils/*test-system*)
        url (test-utils/get-server-url server)]
        ;url "https://linkboard.bogoyavlensky.com"]
        ;url "http://host.testcontainers.internal:8000"]
    (->> {:insert-into :user
          :values [{:sync_code "test-sync-code"}]}
      (db/exec-one! db))

    (etaoin/go driver url)
    (etaoin/refresh driver)
    (etaoin/wait-visible driver {:tag :h1
                                 :fn/has-text "Linkboard"}
                      {:timeout 5})

    (is (= 2 (count (db/exec! db {:select [:*]
                                  :from [:user]}))))

    (is (= 2 (+ 1 1)))))

(comment
  (def driver (atom (e/chrome-headless {:port 4444 :host "localhost" :args ["--no-sandbox"]})))

  (let [d @driver]
    (e/go d "https://linkboard.bogoyavlensky.com")
    (e/refresh d)
    (e/wait-visible d {:tag :h1
                       :fn/has-text "Linkboard"}
                      {:timeout 5})))
    ;(e/query @driver {:tag :h1})))
