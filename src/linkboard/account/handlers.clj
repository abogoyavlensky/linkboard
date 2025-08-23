(ns linkboard.account.handlers
  (:require [clojure.string :as str]
            [linkboard.account.views :as views]
            [linkboard.core.db :as db]
            [linkboard.queries :as q]
            [linkboard.routes :as-alias r]
            [linkboard.ui.components :as c]
            [reitit-extras.core :as ext]
            [ring.util.response :as response])
  (:import [java.time LocalDateTime]
           [java.time.format DateTimeFormatter]))

(defn account-handler
  "Display account page for registered users."
  [{{:keys [db]} :context
    :keys [session]
    :as request}]
  (let [user (q/get-user-by-session-id db (:session-id session))]
    (cond
      ; User not found or not registered (no account number)
      (or (not user) (not (:account-number user)))
      (-> (response/response "Account not found")
          (response/status 404))

      (not (c/hx-request? request))
      ; Full page response
      (->> (views/account-view request {:user user})
           (c/body request)
           (c/base)
           (ext/render-html))

      :else
      ; HTMX page response
      (->> (views/account-view request {:user user})
           (c/body request)
           (ext/render-html)))))

(defn export-data-handler
  "Export user's data to CSV format."
  [{{:keys [db]} :context
    :keys [session]}]
  (let [user (q/get-user-by-session-id db (:session-id session))]
    (cond
      ; User not found or not registered
      (or (not user) (not (:account-number user)))
      (-> (response/response "Access denied")
          (response/status 403))

      :else
      (let [; Query all user's boards and links
            data (->> {:select [[:b.title :board-title] :l.title :l.url :l.created-at]
                       :from [[:link :l]]
                       :left-join [[:board :b] [:= :l.board-id :b.id]]
                       :where [:= :l.user-id (:id user)]
                       :order-by [[:b.title :asc] [:l.created-at :desc]]}
                      (db/exec! db))
            ; Format as CSV
            csv-header "board_title,link_title,url,created_at\n"
            csv-rows (map (fn [{:keys [board-title title url created-at]}]
                            (str (or board-title "No Board") ","
                                 "\"" (or title url) "\","
                                 "\"" url "\","
                                 created-at))
                          data)
            csv-content (str csv-header (str/join "\n" csv-rows))
            timestamp (.format (LocalDateTime/now) (DateTimeFormatter/ofPattern "yyyy-MM-dd_HH-mm-ss"))
            filename (str "linkboard_export_" timestamp ".csv")]

        (-> (response/response csv-content)
            (response/header "Content-Type" "text/csv; charset=utf-8")
            (response/header "Content-Disposition" (str "attachment; filename=\"" filename "\"")))))))

(defn delete-account-handler
  "Delete user account and all associated data."
  [{{:keys [db]} :context
    :keys [session]
    router :reitit.core/router}]
  (let [user (q/get-user-by-session-id db (:session-id session))]
    (cond
      ; User not found or not registered
      (or (not user) (not (:account-number user)))
      (-> (response/response "Access denied")
          (response/status 403))

      :else
      (do
        ; Delete user (CASCADE will delete all boards and links)
        (->> {:delete-from :user
              :where [:= :id (:id user)]}
             (db/exec-one! db))

        ; Clear session and redirect
        (-> (response/response nil)
            (response/header "HX-Redirect" (ext/route router ::r/home-page))
            (response/header "HX-Trigger" "showAccountDeletionToast")
            (assoc :session nil))))))