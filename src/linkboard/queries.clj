(ns linkboard.queries
  (:require [linkboard.core.db :as db]))

(defn delete-link!
  "Delete a link from the database."
  [db {:keys [link-id board-id]}]
  (->> {:delete-from :link
        :where [:and
                [:= :id link-id]
                [:= :board-id board-id]]}
       (db/exec-one! db)))
