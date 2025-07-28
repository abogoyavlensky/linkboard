(ns linkboard.queries
  (:require [linkboard.core.db :as db]))

(defn get-user-by-session-id
  "Get a user by session_id."
  [db session-id]
  (->> {:select [:*]
        :from [:user]
        :where [:= :session-id session-id]}
       (db/exec-one! db)))

(defn update-user-account-number!
  "Update user's account_number by user id."
  [db user-id hashed-account-number]
  (->> {:update :user
        :set {:account-number hashed-account-number}
        :where [:= :id user-id]
        :returning [:*]}
       (db/exec-one! db)))

(defn create-user!
  "Create a new user with session_id and account_number."
  [db session-id hashed-account-number]
  (->> {:insert-into :user
        :values [{:session-id session-id
                  :account-number hashed-account-number}]
        :returning [:*]}
       (db/exec-one! db)))

(defn delete-link!
  "Delete a link from the database."
  [db {:keys [link-id board-id]}]
  (->> {:delete-from :link
        :where [:and
                [:= :id link-id]
                [:= :board-id board-id]]}
       (db/exec-one! db)))
