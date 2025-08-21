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
   [:h3 {:class ["text-lg" "font-semibold" "text-gray-900" "mb-4"]} "Account Information"]
   [:div {:class ["space-y-3"]}
    [:div {:class ["flex" "justify-between" "items-center"]}
     [:span {:class ["text-sm" "text-gray-600"]} "Member Since"]
     [:span {:class ["text-sm" "font-medium" "text-gray-900"]}
      (format-date (:created-at user))]]]])

(defn action-buttons-section
  "Action buttons section."
  [{router :reitit.core/router}]
  [:div {:class ["bg-white" "rounded-xl" "p-6" "shadow-xs"]}
   [:h3 {:class ["text-lg" "font-semibold" "text-gray-900" "mb-4"]} "Account Actions"]
   [:div {:class ["space-y-4"]}

    ; Logout Button
    [:div {:class ["flex" "items-center" "justify-between" "p-4" "border" "rounded-lg" "hover:bg-gray-50"]}
     [:div
      [:h4 {:class ["font-medium" "text-gray-900"]} "Logout"]
      [:p {:class ["text-sm" "text-gray-600"]} "Sign out of your account"]]
     [:button {:class ["bg-gray-500" "text-white" "px-4" "py-2" "rounded-lg" "hover:bg-gray-600" "transition-colors" "cursor-pointer"]
               :hx-post (ext/get-route router ::r/logout)
               :hx-headers (ext/csrf-token-json)}
      "Logout"]]

    ; Export Data Button
    [:div {:class ["flex" "items-center" "justify-between" "p-4" "border" "rounded-lg" "hover:bg-gray-50"]}
     [:div
      [:h4 {:class ["font-medium" "text-gray-900"]} "Export Data"]
      [:p {:class ["text-sm" "text-gray-600"]} "Download all your bookmarks as CSV"]]
     [:a {:href (ext/get-route router ::r/export-data)
          :class ["bg-blue-500" "text-white" "px-4" "py-2" "rounded-lg" "hover:bg-blue-600" "transition-colors"]}
      "Export"]]

    ; Delete Account Button
    [:div {:class ["flex" "items-center" "justify-between" "p-4" "border" "border-red-200" "rounded-lg" "hover:bg-red-50"]}
     [:div
      [:h4 {:class ["font-medium" "text-red-900"]} "Delete Account"]
      [:p {:class ["text-sm" "text-red-600"]} "Permanently delete your account and all data"]]
     (c/modal
       {:open-btn-text [:button {:class ["bg-red-500" "text-white" "px-4" "py-2" "rounded-lg" "hover:bg-red-600" "transition-colors" "cursor-pointer"]}
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
        :form-attrs {:hx-delete (ext/get-route router ::r/account)
                     :hx-headers (ext/csrf-token-json)}})]]])

(defn account-view
  "Main account page view."
  [{router :reitit.core/router
    :as request} {:keys [user]}]
  [:div {:class ["flex-1" "px-4"]}
   ; Header
   [:div {:class ["flex" "items-center" "gap-2" "mb-6"]}
    [:a {:class ["text-blue-500" "hover:text-blue-600"]
         :hx-get (ext/get-route router ::r/home-page)
         :hx-target "#body"
         :hx-push-url "true"}
     icons/chevron-left]
    [:h2 {:class ["text-2xl" "font-bold"]} "Account Settings"]]

   ; Content
   [:div {:class ["max-w-4xl"]}
    (account-info-section user)
    (action-buttons-section request)]])