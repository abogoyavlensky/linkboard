(ns linkboard.account.views
  (:require [linkboard.routes :as-alias r]
            [linkboard.ui.components :as c]
            [linkboard.ui.icons :as icons]
            [reitit-extras.core :as ext])
  (:import [java.time LocalDateTime]
           [java.time.format DateTimeFormatter]))

(defn format-date
  "Format date for display."
  [date-str]
  (when date-str
    (try
      (let [date (LocalDateTime/parse date-str)
            formatter (DateTimeFormatter/ofPattern "MMM d, yyyy")]
        (.format date formatter))
      (catch Exception _ date-str))))

(defn account-info-section
  "Account information section."
  [user]
  [:div {:class ["bg-white" "rounded-xl" "p-6" "mb-6" "shadow-xs"]}
   [:h3 {:class ["text-lg" "font-semibold" "text-gray-900" "mb-4"]} "Information"]
   [:div {:class ["space-y-3"]}

    ; Account Number Row
    [:div {:class ["flex" "justify-between" "items-center" "gap-2"]}
     [:span {:class ["text-sm" "text-gray-600" "flex-shrink-0"]} "Account Number"]
     [:div {:class ["flex" "items-center" "gap-1" "flex-shrink" "min-w-0"]
            :x-data (str "{ showAccountNumber: false, copySuccess: false, accountNumber: '" (:account-number user) "' }")}
      ; Account number display (dots or actual number)
      [:span {:class ["text-xs" "sm:text-sm" "font-medium" "text-gray-900" "cursor-pointer" "select-none" "font-mono" "whitespace-nowrap"]
              :x-show "!showAccountNumber"
              :x-on:click "navigator.clipboard.writeText(accountNumber); copySuccess = true; setTimeout(() => copySuccess = false, 2000)"}
       "••••••••"]
      [:span {:class ["text-xs" "sm:text-sm" "font-medium" "text-gray-900" "cursor-pointer" "select-none" "font-mono" "whitespace-nowrap"]
              :x-show "showAccountNumber"
              :x-on:click "navigator.clipboard.writeText(accountNumber); copySuccess = true; setTimeout(() => copySuccess = false, 2000)"}
       (:account-number user)]

      ; Eye toggle button
      [:button {:class ["p-1" "hover:bg-gray-100" "rounded"]
                :x-show "!showAccountNumber"
                :x-on:click "showAccountNumber = true"}
       icons/eye]
      [:button {:class ["p-1" "hover:bg-gray-100" "rounded"]
                :x-show "showAccountNumber"
                :x-on:click "showAccountNumber = false"}
       icons/eye-slash]

      ; Copy button / Success indicator
      [:button {:class ["p-1" "hover:bg-gray-100" "rounded"]
                :x-show "!copySuccess"
                :x-on:click "navigator.clipboard.writeText(accountNumber); copySuccess = true; setTimeout(() => copySuccess = false, 2000)"}
       icons/copy]
      [:button {:class ["p-1" "rounded"]
                :x-show "copySuccess"
                :disabled true}
       icons/check-circle]]]

    ; Member Since Row
    [:div {:class ["flex" "justify-between" "items-center"]}
     [:span {:class ["text-sm" "text-gray-600"]} "Member Since"]
     [:span {:class ["text-sm" "font-medium" "text-gray-900"]}
      (format-date (:created-at user))]]]])

(defn account-limit-section
  "Account limits information section."
  [{:keys [board-count link-count]}]
  [:div {:class ["bg-white" "rounded-xl" "p-6" "mb-6" "shadow-xs"]}
   [:h3 {:class ["text-lg" "font-semibold" "text-gray-900" "mb-4"]} "Limits"]
   [:div {:class ["space-y-3"]}

    ; Board Limit Row
    [:div {:class ["flex" "justify-between" "items-center"]}
     [:span {:class ["text-sm" "text-gray-600"]} "Board Limit"]
     [:span {:class ["text-sm" "font-medium" "text-gray-900"]}
      [:span {:class ["text-blue-600" "font-bold"]} (str board-count "/50")]]]

    ; Link Limit Row
    [:div {:class ["flex" "justify-between" "items-center"]}
     [:span {:class ["text-sm" "text-gray-600"]} "Link Limit"]
     [:span {:class ["text-sm" "font-medium" "text-gray-900"]}
      [:span {:class ["text-blue-600" "font-bold"]} (str link-count "/5,000")]]]]])

(defn action-buttons-section
  "Action buttons section."
  [{router :reitit.core/router}]
  [:div {:class ["bg-white" "rounded-xl" "p-6" "shadow-xs"]}
   [:h3 {:class ["text-lg" "font-semibold" "text-gray-900" "mb-4"]} "Actions"]
   [:div {:class ["space-y-4"]}

    ; Logout Button
    [:div {:class ["flex" "items-center" "justify-between" "p-4" "border" "rounded-lg" "hover:bg-gray-50"]}
     [:div
      [:h4 {:class ["font-medium" "text-gray-900"]} "Logout"]
      [:p {:class ["text-sm" "text-gray-600"]} "Sign out of your account"]]
     [:button {:class ["bg-gray-500" "text-white" "px-4" "py-2" "rounded-lg" "hover:bg-gray-600" "transition-colors" "cursor-pointer"]
               :hx-post (ext/route router ::r/logout)
               :hx-headers (ext/csrf-token-json)}
      "Logout"]]

    ; Export Data Button
    [:div {:class ["flex" "items-center" "justify-between" "p-4" "border" "rounded-lg" "hover:bg-gray-50"]}
     [:div
      [:h4 {:class ["font-medium" "text-gray-900"]} "Export Data"]
      [:p {:class ["text-sm" "text-gray-600"]} "Download all your bookmarks as CSV"]]
     [:a {:href (ext/route router ::r/export-data)
          :class ["bg-blue-500" "text-white" "px-4" "py-2" "rounded-lg" "hover:bg-blue-600" "transition-colors"]}
      "Export"]]

    ; Delete Account Button
    [:div {:class ["flex" "items-center" "justify-between" "p-4" "border" "border-red-200" "rounded-lg" "hover:bg-red-50"]}
     [:div
      [:h4 {:class ["font-medium" "text-red-900"]} "Delete Account"]
      [:p {:class ["text-sm" "text-red-600"]} "Permanently delete your account and all data"]]
     (c/modal
       {:open-btn-text [:div {:class ["bg-red-500" "text-white" "px-4" "py-2" "rounded-lg" "hover:bg-red-600" "transition-colors" "cursor-pointer"]}
                        "Delete"]
        :title "Delete Account"
        :submit-btn-title "Delete Account"
        :form-fields [:div {:class ["space-y-4"]}
                      [:div {:class ["bg-red-50" "border" "border-red-200" "rounded-lg" "p-4"]}
                       [:div {:class ["flex" "items-start"]}
                        [:div {:class ["flex-shrink-0"]}
                         [:svg {:class ["h-5" "w-5" "text-red-400"]
                                :viewBox "0 0 20 20"
                                :fill "currentColor"}
                          [:path {:fill-rule "evenodd"
                                  :d "M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z"
                                  :clip-rule "evenodd"}]]]
                        [:div {:class ["ml-3"]}
                         [:h3 {:class ["text-sm" "font-medium" "text-red-800"]} "Warning"]
                         [:div {:class ["mt-2" "text-sm" "text-red-700"]}
                          [:p "This action cannot be undone. This will permanently delete your account, all your boards, and all your bookmarks."]]]]]
                      [:p {:class ["text-gray-700" "font-medium"]} "Type DELETE to confirm:"]
                      [:input {:type "text"
                               :name "confirmation"
                               :class ["w-full" "px-3" "py-2" "border" "rounded-md"]
                               :placeholder "DELETE"
                               :required true}]]
        :form-attrs {:hx-delete (ext/route router ::r/account)
                     :hx-headers (ext/csrf-token-json)}})]]])

(defn account-view
  "Main account page view."
  [{:as request} {:keys [user board-count link-count]}]
  [:div {:class ["flex-1" "px-4"]}
   ; Header
   [:div {:class ["flex" "items-center" "gap-2" "mb-6"]}
    (c/back-button request)
    [:h2 {:class ["text-2xl" "font-bold"]} "Settings"]]

   ; Content
   [:div {:class ["max-w-4xl"]}
    (account-info-section user)
    (account-limit-section {:board-count board-count :link-count link-count})
    (action-buttons-section request)]])