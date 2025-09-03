(ns linkboard.test-utils
  (:require [clojure.java.shell :as shell]
            [clojure.string :as str]
            [integrant-extras.tests :as ig-extras]
            [linkboard.core.db :as db]
            [linkboard.core.server :as server]))

(def ^:const TEST-CSRF-TOKEN "test-csrf-token")
(def ^:const TEST-SECRET-KEY "test-secret-key")

(defn- all-tables
  [db]
  (->> {:select [:name]
        :from [:sqlite_master]
        :where [:= :type "table"]}
       (db/exec! db)
       (map (comp keyword :name))))

(defn with-truncated-tables
  "Remove all data from all tables."
  [f]
  (let [db (::db/db ig-extras/*test-system*)]
    (doseq [table (all-tables db)
            :when (not= :schema_version table)]
      (db/exec! db {:delete-from table}))
    (f)))

(defn ->db
  "Get the database connection from the test system."
  []
  (::db/db ig-extras/*test-system*))

(defn ->server
  "Get the server instance from the test system."
  []
  (::server/server ig-extras/*test-system*))

(def driver-path!
  (memoize
    (fn []
      (-> (shell/sh "mise" "which" "chromedriver")
          :out
          (str/trim)))))

; DB queries

(defn get-all-boards
  [db]
  (db/exec! db {:select [:*]
                :from [:board]
                :order-by [[:id :desc]]}))
