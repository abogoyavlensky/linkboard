(ns linkboard.test-utils
  (:require [integrant-extras.tests :as ig-extras]
            [linkboard.core.db :as db]
            [linkboard.core.server :as server]
            [integrant.core :as ig])
  (:import (io.github.bonigarcia.wdm WebDriverManager)))

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

(defn setup-webdriver! []
  ; This automatically downloads the correct ChromeDriver version
  ; and return chromedriver path
  (let [manager (WebDriverManager/chromedriver)]
    (.setup manager)
    (.getDownloadedDriverPath manager)))

(defmethod ig/init-key ::webdriver-path
  [_ _]
  (setup-webdriver!))

(defn ->webdriver-path
  []
  (::webdriver-path ig-extras/*test-system*))

; DB queries

(defn get-all-boards
  [db]
  (db/exec! db {:select [:*]
                :from [:board]
                :order-by [[:id :desc]]}))
