(ns linkboard.test-utils
  (:require [etaoin.api :as e]
            [integrant-extras.tests :as ig-extras]
            [integrant.core :as ig]
            [linkboard.core.db :as db]
            [linkboard.core.server :as server])
  (:import (io.github.bonigarcia.wdm WebDriverManager)))

(def ^:const TEST-CSRF-TOKEN "test-csrf-token")
(def ^:const TEST-SECRET-KEY "test-secret-key")

(defn- all-tables
  [db]
  (->> {:select [:name]
        :from [:sqlite_master]
        :where [:and
                [:= :type "table"]
                [:<> :name "link_search"]
                [:<> :name "link_search_data"]
                [:<> :name "link_search_idx"]
                [:<> :name "link_search_docsize"]
                [:<> :name "link_search_config"]]}
       (db/exec! db)
       (map (comp keyword :name))))

(defn with-truncated-tables
  "Remove all data from all tables except virtual tables (e.g., FTS5)."
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

(defmethod ig/init-key ::webdriver-path
  [_ _]
  ; This automatically downloads the correct ChromeDriver version
  ; and return chromedriver path
  (let [manager (WebDriverManager/chromedriver)]
    (.setup manager)
    (.getDownloadedDriverPath manager)))

(defn ->webdriver-path
  []
  (::webdriver-path ig-extras/*test-system*))

(defmacro with-chrome
  "Wrapper macro for etaoin with-chrome-headless that includes predefined :path-driver option."
  [driver & body]
  `(e/with-chrome-headless {:path-driver (->webdriver-path)} ~driver
     ~@body))

; DB queries

(defn get-all-boards
  [db]
  (db/exec! db {:select [:*]
                :from [:board]
                :order-by [[:id :desc]]}))

(defn get-all-links
  [db]
  (db/exec! db {:select [:*]
                :from [:link]
                :order-by [[:id :desc]]}))
