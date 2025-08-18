(ns linkboard.queries
  (:require [linkboard.core.db :as db]))

(defn get-user-by-session-id
  "Get a user by session_id."
  [db session-id]
  (->> {:select [:*]
        :from [:user]
        :where [:= :session-id session-id]}
       (db/exec-one! db)))

(defn get-user-by-account-number
  "Get a user by hashed account number."
  [db account-number]
  (->> {:select [:*]
        :from [:user]
        :where [:= :account-number account-number]}
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

(defn create-user-with-session!
  "Create a new user with only session_id (empty account_number)."
  [db session-id]
  (->> {:insert-into :user
        :values [{:session-id session-id}]
        :returning [:*]}
       (db/exec-one! db)))

(defn ensure-user-exists!
  "Get user by session_id or create one if doesn't exist. Returns user record."
  [db session-id]
  (if-let [user (get-user-by-session-id db session-id)]
    user
    (create-user-with-session! db session-id)))

(defn get-board-by-id-and-user-id
  "Get a board by ID if it belongs to the specified user."
  [db board-id user-id]
  (->> {:select [:*]
        :from [:board]
        :where [:and
                [:= :id board-id]
                [:= :user-id user-id]]}
       (db/exec-one! db)))

(defn user-owns-board?
  "Check if the user owns the board."
  [db {:keys [board-id session-id]}]
  (some->> (get-user-by-session-id db session-id)
           :id
           (get-board-by-id-and-user-id db board-id)
           (boolean)))

(defn user-owns-link?
  "Check if the user owns the link."
  [db {:keys [link-id session-id]}]
  (->> {:select [1]
        :from [:link]
        :join [:user [:= :link.user-id :user.id]]
        :where [:and
                [:= :link.id link-id]
                [:= :user.session-id session-id]]}
       (db/exec-one! db)
       (boolean)))

(defn delete-link!
  "Delete a link from the database."
  [db {:keys [link-id user-id]}]
  (->> {:delete-from :link
        :where [:and
                [:= :id link-id]
                [:= :user-id user-id]]}
       (db/exec-one! db)))

(defn search-all-links-query
  "Build query for searching all user links using FTS5.
   Returns HoneySQL query map that can be used with pagination."
  [user-id search-term]
  {:select [:l.* [:b.title :board-title] [:b.id :board-id] [[:bm25 :link-search] :search-rank]]
   :from [[:link :l]]
   :join [[:link-search :ls] [:= :l.id :ls.rowid]]
   :left-join [[:board :b] [:= :l.board-id :b.id]]
   :where [:and
           [:= :l.user-id user-id]
           [:raw "link_search MATCH " [search-term]]]
   :order-by [[:search-rank :asc] [:l.created-at :desc]]})

(defn search-board-links-query
  "Build query for searching links within a specific board using FTS5.
   Returns HoneySQL query map that can be used with pagination."
  [user-id board-id search-term]
  {:select [:l.* [[:bm25 :link-search] :search-rank]]
   :from [[:link :l]]
   :join [[:link-search :ls] [:= :l.id :ls.rowid]
          [:board :b] [:= :l.board-id :b.id]]
   :where [:and
           [:= :l.user-id user-id]
           [:= :b.id board-id]
           [:= :b.user-id user-id]
           [:raw "link_search MATCH " [search-term]]]
   :order-by [[:search-rank :asc] [:l.created-at :desc]]})

(defn get-all-links-query
  "Build query for getting all user links, optionally with search.
   Returns HoneySQL query map that can be used with pagination."
  [user-id search-term]
  (if search-term
    (search-all-links-query user-id search-term)
    {:select [:l.* [:b.title :board-title] [:b.id :board-id]]
     :from [[:link :l]]
     :left-join [[:board :b] [:= :l.board-id :b.id]]
     :where [:= :l.user-id user-id]
     :order-by [[:l.created-at :desc]]}))

(defn get-board-links-query
  "Build query for getting links within a specific board, optionally with search.
   Returns HoneySQL query map that can be used with pagination."
  [user-id board-id search-term]
  (if search-term
    (search-board-links-query user-id board-id search-term)
    {:select [:l.*]
     :from [[:link :l]]
     :join [[:board :b] [:= :l.board-id :b.id]]
     :where [:and
             [:= :b.user-id user-id]
             [:= :b.id board-id]]
     :order-by [[:l.created-at :desc]]}))

(comment
  (require '[integrant.repl.state :as state])
  (let [db (:linkboard.core.db/db state/system)]
    (db/exec! db (get-board-links-query 2 3 "Gi*"))))
