(ns linkboard.home-test
  (:require [clojure.test :refer :all]
            [linkboard.db :as db]
            [linkboard.test-utils :as test-utils]))

(use-fixtures :each
  test-utils/with-system)

(deftest test-example
  (let [db (::db/db test-utils/*test-system*)]
    (->> {:insert-into :user
          :values [{:sync_code "test-sync-code"}]}
      (db/exec-one! db))

    (is (= 2 (count (db/exec! db {:select [:*]
                                  :from [:user]}))))

    (is (= 2 (+ 1 1)))))
