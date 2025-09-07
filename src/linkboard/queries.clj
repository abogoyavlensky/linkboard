(ns linkboard.queries
  (:require [buddy.hashers :as hashers]
            [clojure.string :as str]
            [linkboard.core.db :as db]))

(def ^:const PASSWORD-HASH-ALGORITHM :bcrypt+sha512)

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

(defn account-number->creds
  [account-number]
  (let [number-split (str/split account-number #"-")
        account-lookup-id (->> number-split
                               (take 2)
                               (str/join "-"))
        password (->> number-split
                      (drop 2)
                      (str/join "-"))]
    {:account-lookup-id account-lookup-id
     :password password}))

(defn update-user-account-number!
  "Update user's account_number by user id."
  [db user-id account-number]
  (let [{:keys [account-lookup-id password]} (account-number->creds account-number)]
    (->> {:update :user
          :set {:account-number account-lookup-id
                :password (hashers/derive password {:alg PASSWORD-HASH-ALGORITHM})}
          :where [:= :id user-id]
          :returning [:*]}
         (db/exec-one! db))))

(defn create-user!
  "Create a new user with session_id and account_number."
  [db session-id account-number]
  (let [{:keys [account-lookup-id password]} (account-number->creds account-number)]
    (->> {:insert-into :user
          :values [{:session-id session-id
                    :account-number account-lookup-id
                    :password (hashers/derive password {:alg PASSWORD-HASH-ALGORITHM})}]
          :returning [:*]}
         (db/exec-one! db))))

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

(defn get-link-by-id-and-user-id
  "Get a link by ID if it belongs to the specified user."
  [db link-id user-id]
  (->> {:select [:*]
        :from [:link]
        :where [:and
                [:= :id link-id]
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

(defn toggle-board-favorite!
  "Toggle the favorite status of a board."
  [db {:keys [board-id user-id]}]
  (let [updated-board (->> {:update :board
                            :set {:favorite [:not :favorite]}
                            :where [:and
                                    [:= :id board-id]
                                    [:= :user-id user-id]]
                            :returning [:*]}
                           (db/exec-one! db))]
    (update updated-board :favorite #(> % 0))))

(defn toggle-link-favorite!
  "Toggle the favorite status of a link."
  [db {:keys [link-id user-id]}]
  (let [updated-link (->> {:update :link
                           :set {:favorite [:not :favorite]}
                           :where [:and
                                   [:= :id link-id]
                                   [:= :user-id user-id]]
                           :returning [:*]}
                          (db/exec-one! db))]
    (update updated-link :favorite #(> % 0))))

(defn delete-link!
  "Delete a link from the database."
  [db {:keys [link-id user-id]}]
  (->> {:delete-from :link
        :where [:and
                [:= :id link-id]
                [:= :user-id user-id]]}
       (db/exec-one! db)))

(defn get-user-board-count
  "Get the count of boards for a user."
  [db user-id]
  (->> {:select [[[:count :id] :board-count]]
        :from [:board]
        :where [:= :user-id user-id]}
       (db/exec-one! db)
       :board-count))

(defn get-user-link-count
  "Get the count of links for a user."
  [db user-id]
  (->> {:select [[[:count :id] :link-count]]
        :from [:link]
        :where [:= :user-id user-id]}
       (db/exec-one! db)
       :link-count))

(defn preprocess-search-query
  "Preprocess user search input for FTS5 MATCH query.
   
   Steps:
   1. Split input into tokens by whitespace
   2. Quote tokens containing special chars (., @, /, -, :, \", *, etc.)
   3. Add wildcards (*) at the end of tokens for partial matching
   4. Normalize by lowercasing and stripping extra punctuation
   5. Return processed query or nil if empty/invalid
   
   Examples:
   'openai.com cool stuff' -> '\"openai.com\"* cool* stuff*'
   'github AND code' -> 'github* AND code*'
   '' -> nil"
  [raw-query]
  (when (and raw-query (not (str/blank? raw-query)))
    (let [; Step 1: Split into tokens
          tokens (str/split (str/trim raw-query) #"\s+")

          ; Step 4: Normalize - lowercase and strip extra punctuation
          normalize-token (fn [token]
                            (-> token
                                str/lower-case
                                (str/replace #"[(){}\[\],;]+" "")))

          ; Step 2: Quote tokens with special chars and Step 3: Add wildcards
          process-token (fn [token]
                          (let [normalized (normalize-token token)
                               ; Don't quote FTS5 operators
                                is-operator? (contains? #{"and" "or" "not" "near"} normalized)
                               ; Check if token needs quoting (contains special chars)
                                needs-quoting? (re-find #"[.@/\-:\"*]" normalized)]
                            (cond
                             ; Empty token after normalization
                              (str/blank? normalized) nil

                             ; FTS5 operators - keep as is, no wildcard
                              is-operator? normalized

                             ; Token with special chars - quote and add wildcard
                              needs-quoting? (str "\"" normalized "\"*")

                             ; Regular token - just add wildcard
                              :else (str normalized "*"))))

          ; Process all tokens and filter out nils
          processed-tokens (->> tokens
                                (map process-token)
                                (filter some?))]

      ; Return processed query or nil if no valid tokens
      (when (seq processed-tokens)
        (str/join " " processed-tokens)))))

(defn search-all-links-query
  "Build query for searching all user links using FTS5.
   Returns HoneySQL query map that can be used with pagination."
  [user-id search-term raw-search-term]
  (cond-> {:from [[:link :l]]
           :left-join [[:board :b] [:= :l.board-id :b.id]]}
    (>= (count raw-search-term) 3) (assoc :select [:l.* [:b.title :board-title] [:b.id :board-id] [[:bm25 :link-search] :search-rank]]
                                          :join [[:link-search :ls] [:= :l.id :ls.rowid]]
                                          :where [:and
                                                  [:= :l.user-id user-id]
                                                  [:raw "link_search MATCH " [search-term]]]
                                          :order-by [[:search-rank :asc] [:l.favorite :desc] [:l.created-at :desc]])
    (< (count raw-search-term) 3) (assoc :select [:l.* [:b.title :board-title] [:b.id :board-id]]
                                         :where [:and
                                                 [:= :l.user-id user-id]
                                                 [:like :l.title (str "%" raw-search-term "%")]]
                                         :order-by [[:l.favorite :desc] [:l.created-at :desc]])))

(defn search-board-links-query
  "Build query for searching links within a specific board using FTS5 or LIKE for short terms.
   Returns HoneySQL query map that can be used with pagination."
  [user-id board-id search-term raw-search-term]
  (cond-> {:from [[:link :l]]
           :join [[:board :b] [:= :l.board-id :b.id]]}
    (>= (count raw-search-term) 3) (assoc :select [:l.* [[:bm25 :link-search] :search-rank]]
                                          :join [[:link-search :ls] [:= :l.id :ls.rowid]
                                                 [:board :b] [:= :l.board-id :b.id]]
                                          :where [:and
                                                  [:= :l.user-id user-id]
                                                  [:= :b.id board-id]
                                                  [:= :b.user-id user-id]
                                                  [:raw "link_search MATCH " [search-term]]]
                                          :order-by [[:search-rank :asc] [:l.favorite :desc] [:l.created-at :desc]])
    (< (count raw-search-term) 3) (assoc :select [:l.*]
                                         :where [:and
                                                 [:= :l.user-id user-id]
                                                 [:= :b.id board-id]
                                                 [:= :b.user-id user-id]
                                                 [:like :l.title (str "%" raw-search-term "%")]]
                                         :order-by [[:l.favorite :desc] [:l.created-at :desc]])))

(defn get-all-links-query
  "Build query for getting all user links, optionally with search.
   Returns HoneySQL query map that can be used with pagination."
  [user-id search-term]
  (if-let [processed-search (and search-term (preprocess-search-query search-term))]
    (search-all-links-query user-id processed-search search-term)
    {:select [:l.* [:b.title :board-title] [:b.id :board-id]]
     :from [[:link :l]]
     :left-join [[:board :b] [:= :l.board-id :b.id]]
     :where [:= :l.user-id user-id]
     :order-by [[:l.favorite :desc] [:l.created-at :desc]]}))

(defn get-board-links-query
  "Build query for getting links within a specific board, optionally with search.
   Returns HoneySQL query map that can be used with pagination."
  [user-id board-id search-term]
  (if-let [processed-search (and search-term (preprocess-search-query search-term))]
    (search-board-links-query user-id board-id processed-search search-term)
    {:select [:l.*]
     :from [[:link :l]]
     :join [[:board :b] [:= :l.board-id :b.id]]
     :where [:and
             [:= :b.user-id user-id]
             [:= :b.id board-id]]
     :order-by [[:l.favorite :desc] [:l.created-at :desc]]}))

(defn get-user-boards-minimal
  "Get minimal board data (id and title) for a user, ordered by title."
  [db user-id]
  (->> {:select [:id :title]
        :from [:board]
        :where [:= :user-id user-id]
        :order-by [:title]}
       (db/exec! db)))

(comment
  ; Test search preprocessing
  (preprocess-search-query "openai.com cool stuff")
  ; => "\"openai.com\"* cool* stuff*"

  (preprocess-search-query "github AND code")
  (preprocess-search-query "gog")
  ; => "github* and code*"

  (preprocess-search-query "   ")
  ; => nil

  ; Test with database
  (require '[integrant.repl.state :as state])
  (let [db (:linkboard.core.db/db state/system)]
    (db/exec! db (get-all-links-query 2 "gog.co"))))
