(ns linkboard.board.views
  (:require [clojure.string :as str]
            [linkboard.routes :as-alias r]
            [linkboard.ui.components :as c]
            [linkboard.ui.icons :as icons]
            [reitit-extras.core :as reitit-extras]))

(defn link-edit-form-fields
  [request {:keys [link]}]
  (let [errors (get-in request [:errors :humanized])]
    [:div
     {:id "link-edit-form-fields"}
     [:div.mb-4
      [:label.block.text-sm.font-medium.text-gray-700.mb-1 {:for "title"} "Title"]
      [:input
       {:type "text"
        :name "title"
        :class (concat ["w-full" "px-3" "py-2" "border" "rounded-md" "text-sm"]
                       (when (seq (:title errors))
                         ["border-red-500" "focus:border-red-500" "focus:ring-red-500"]))
        :id "title"
        :value (or (:title link) "")
        :placeholder "Link title"}]]
     (for [error (:title errors)]
       [:p {:class ["text-red-500" "text-sm" "mt-1"]} (str/capitalize error)])
     [:div
      [:label.block.text-sm.font-medium.text-gray-700.mb-1 {:for "url"} "URL"]
      [:input
       {:type "text"
        :name "url"
        :class (concat ["w-full" "px-3" "py-2" "border" "rounded-md" "text-sm"]
                       (when (seq (:url errors))
                         ["border-red-500" "focus:border-red-500" "focus:ring-red-500"]))
        :id "url"
        :value (:url link)
        :placeholder "https://example.com"}]]
     (for [error (:url errors)]
       [:p {:class ["text-red-500" "text-sm" "mt-1"]} (str/capitalize error)])]))

(defn- link-list-item
  [{:keys [request router link board]}]
  [:div.link-item {:class ["w-full" "bg-white" "rounded-xl" "mb-2" "p-4" "flex"
                           "items-center" "shadow-xs"]}
   [:a {:class ["flex" "items-center" "gap-3" "flex-grow" "min-w-0" "mr-4"]
        :href (:url link)
        :rel "noopener noreferrer"
        :target "_blank"}
    (if (and (:icon link) (seq (:icon link)))
      [:img {:src (:icon link)
             :class ["w-5" "h-5" "flex-shrink-0"]
             :onerror "this.onerror=null; this.src=''; this.classList.add('hidden');"
             :alt "Site icon"}]
      icons/bookmark)
    [:div {:class ["min-w-0" "flex-grow" "max-w-full"]}
     [:span {:class ["text-l" "break-words" "block" "w-full"]}
      (:title link)]
     [:p {:class ["text-gray-400" "truncate" "block" "w-full"]} (:url link)]]]
   [:div {:class ["flex" "items-center" "gap-2" "flex-shrink-0"]}
    ; TODO: reimplement edit link endpoint without board required
    (c/modal
      {:open-btn-text (icons/edit)
       :title "Edit link"
       :submit-btn-title "Save changes"
       :form-fields (link-edit-form-fields request {:link link})
       :form-attrs {:hx-put (reitit-extras/get-route
                              router
                              ::r/link-details
                              {:path {:id (:id board)
                                      :link-id (:id link)}})
                    :hx-headers (reitit-extras/csrf-token-json)
                    :hx-target "#link-edit-form-fields"}})
    (c/modal
      {:open-btn-text icons/bin
       :title "Delete link"
       :submit-btn-title "Confirm"
       :form-fields [:div
                     [:p {:class ["text-md text-gray-600" "mb-2"]}
                      "Are you sure you want to delete following link?"]
                     [:b {:class ["text-gray-900" "font-semibold" "line-clamp-3"]}
                      (or (:title link) (:url link))]]
       :form-attrs {:hx-delete (reitit-extras/get-route
                                 router
                                 ::r/link-details
                                 {:path {:id (:id board)
                                         :link-id (:id link)}})
                    :hx-headers (reitit-extras/csrf-token-json)
                    :hx-target "closest .link-item"
                    :hx-swap "outerHTML"}})]])

(defn link-form-fields
  [request]
  (let [errors (get-in request [:errors :humanized :url])]
    [:div
     {:id "link-form-fields"}
     [:input
      {:class (concat ["flex" "w-full" "h-10" "px-3" "py-2" "text-sm"
                       "bg-white" "border" "rounded-md" "border-neutral-300"
                       "ring-offset-background" "placeholder:text-neutral-500"
                       "focus:border-neutral-300" "focus:outline-hidden"
                       "focus:ring-2" "focus:ring-offset-2" "focus:ring-neutral-400"
                       "disabled:cursor-not-allowed" "disabled:opacity-50"]
                      (when (seq errors)
                        ["border-red-500" "focus:border-red-500" "focus:ring-red-500"]))
       :type "text"
       :name "url"
       :value (get-in request [:parameters :form :url] nil)
       :minlength 1
       :autofocus true
       :placeholder "Enter link"}]
     (for [error errors]
       [:p {:class ["text-red-500" "text-sm" "mt-1"]} (str/capitalize error)])]))

(defn board-edit-form-fields
  [request {:keys [board]}]
  (let [errors (get-in request [:errors :humanized :title])]
    [:div
     {:id "board-edit-form-fields"}
     [:label.block.text-sm.font-medium.text-gray-700.mb-1 {:for "title"} "Board Title"]
     [:input
      {:type "text"
       :name "title"
       :class (concat ["w-full" "px-3" "py-2" "border" "rounded-md" "text-sm"]
                      (when (seq errors)
                        ["border-red-500" "focus:border-red-500" "focus:ring-red-500"]))
       :id "title"
       :value (:title board)
       :placeholder "Enter board name"}]
     (for [error errors]
       [:p {:class ["text-red-500" "text-sm" "mt-1"]} (str/capitalize error)])]))

(defn board-view
  [{router :reitit.core/router
    :as request} {:keys [board links]}]
  [:div {:class ["flex-1" "px-4"]}
   ; Title, back button and add link button
   [:div {:class ["flex" "justify-between" "items-center" "mb-4"]}
    [:div {:class ["flex" "items-center" "gap-2"]}
     [:a {:class ["text-blue-500" "hover:text-blue-600"]
          :hx-get (reitit-extras/get-route router ::r/home-page)
          :hx-target "#body"
          :hx-push-url "true"}
      icons/chevron-left]
     [:h2 {:class ["text-2xl" "font-bold"]} (:title board)]]
    [:div {:class ["flex" "items-center" "gap-2"]}
     (c/modal
       {:open-btn-text [:div.ml-2.text-gray-500.hover:text-gray-700.cursor-pointer
                        (icons/edit)]
        :title "Edit board"
        :submit-btn-title "Save changes"
        :form-fields (board-edit-form-fields request {:board board})
        :form-attrs {:hx-put (reitit-extras/get-route router ::r/board-details {:path {:id (:id board)}})
                     :hx-headers (reitit-extras/csrf-token-json)
                     :hx-target "#board-edit-form-fields"}})
     (c/modal
       {:open-btn-text [:div.ml-2.text-red-500.hover:text-red-700.cursor-pointer
                        icons/bin]
        :title "Delete board"
        :submit-btn-title "Confirm"
        :form-fields [:div
                      [:p {:class ["text-md text-gray-600" "mb-2"]}
                       "Are you sure you want to delete this board?"]
                      [:p {:class ["text-sm text-gray-600" "mb-2"]}
                       "This will permanently delete the board and all its links."]
                      [:b {:class ["text-gray-900" "font-semibold" "line-clamp-3"]}
                       (:title board)]]
        :form-attrs {:hx-delete (reitit-extras/get-route router ::r/board-details {:path {:id (:id board)}})
                     :hx-headers (reitit-extras/csrf-token-json)}})]]
   ; TODO: remove with old route!
     ;(c/modal
     ;  {:open-btn-text (c/button {:content [:div {:class ["flex" "items-center" "gap-1"]}
     ;                                       icons/plus-circle "Add link"]})
     ;   :title "Add link"
     ;   :form-attrs {:hx-post (reitit-extras/get-route router ::r/board-details-links {:path {:id (:id board)}})
     ;                :hx-target "#link-form-fields"}
     ;   :form-fields (link-form-fields request)})]]

   (if (seq links)
     (list
       (c/search-bar)
       ; Links
       [:div {:class ["flex-1"]}
        (for [link links]
          (link-list-item {:router router
                           :request request
                           :link link
                           :board board}))])
     ; Empty state
     [:div {:class ["text-center" "mx-auto" "mt-16"]}
      [:h2 {:class ["text-2xl" "font-semibold" "text-gray-900" "mb-3"]} "No bookmarks yet"]
      [:p {:class ["text-gray-600" "mb-8"]} "Start building your collection by adding your first link"]])])

(defn all-links-view
  [{router :reitit.core/router
    :as request} {:keys [links]}]
  [:div {:class ["flex-1" "px-4"]}
   ; Title, back button and add link button
   [:div {:class ["flex" "justify-between" "items-center" "mb-4"]}
    [:div {:class ["flex" "items-center" "gap-2"]}
     [:a {:class ["text-blue-500" "hover:text-blue-600"]
          :hx-get (reitit-extras/get-route router ::r/home-page)
          :hx-target "#body"
          :hx-push-url "true"}
      icons/chevron-left]
     [:h2 {:class ["text-2xl" "font-bold"]} "All Links"]]]
   (if (seq links)
     (list
       (c/search-bar)
       ; Links
       [:div {:class ["flex-1"]}
        (for [link links]
          (link-list-item {:router router
                           :request request
                           :link link}))])
     ; Empty state
     [:div {:class ["text-center" "mx-auto" "mt-16"]}
      [:h2 {:class ["text-2xl" "font-semibold" "text-gray-900" "mb-3"]} "No bookmarks yet"]
      [:p {:class ["text-gray-600" "mb-8"]} "Start building your collection by adding your first link"]])])
