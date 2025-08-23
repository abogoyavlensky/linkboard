(ns linkboard.board.views
  (:require [clojure.string :as str]
            [linkboard.routes :as-alias r]
            [linkboard.ui.components :as c]
            [linkboard.ui.icons :as icons]
            [reitit-extras.core :as ext]))

(defn favorite-link-icon
  [link]
  [:div
   {:id (str "favorite-link-icon-" (:id link))}
   (if (:favorite link) icons/star-solid icons/star)])

(defn link-edit-form-fields
  [request {:keys [link boards]}]
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
     [:div.mb-4
      [:label.block.text-sm.font-medium.text-gray-700.mb-1 {:for "board"} "Board"]
      [:select
       {:name "board-id"
        :class (concat ["w-full" "px-3" "py-2" "border" "rounded-md" "text-sm"]
                       (when (seq (:board-id errors))
                         ["border-red-500" "focus:border-red-500" "focus:ring-red-500"]))
        :id "board"}
       [:option {:value ""
                 :selected (nil? (:board-id link))} "No board"]
       (for [board boards]
         [:option {:value (:id board)
                   :selected (= (:id board) (:board-id link))}
          (:title board)])]]
     (for [error (:board-id errors)]
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

(defn link-list-item
  [{:keys [request router link show-board? boards]}]
  [:div.link-item {:id (str "link-" (:id link))
                   :class ["w-full" "bg-white" "rounded-xl" "mb-2" "p-4" "flex"
                           "items-center" "shadow-xs"]}
   [:div {:class ["flex" "items-center" "gap-3" "flex-grow" "min-w-0" "mr-4"]}
    [:a {:href (:url link)
         :rel "noopener noreferrer"
         :target "_blank"
         :class ["flex-shrink-0"]}
     (if (and (:icon link) (seq (:icon link)))
       [:img {:src (:icon link)
              :class ["w-5" "h-5" "flex-shrink-0"]
              :onerror "this.onerror=null; this.src=''; this.classList.add('hidden');"
              :alt "Site icon"}]
       icons/bookmark)]
    [:div {:class ["min-w-0" "flex-grow" "max-w-full"]}
     [:a {:href (:url link)
          :rel "noopener noreferrer"
          :target "_blank"
          :class ["text-l" "break-words" "block" "w-full" "no-underline"]}
      (:title link)]
     [:a {:href (:url link)
          :rel "noopener noreferrer"
          :target "_blank"
          :class ["text-gray-400" "truncate" "block" "w-full" "no-underline"]}
      (if (and show-board? (:board-title link))
        [:span
         [:span {:class ["hover:underline" "cursor-pointer"]
                 :style "color: inherit;"
                 :hx-get (ext/route router ::r/board-details {:path {:id (:board-id link)}})
                 :hx-target "#body"
                 :hx-push-url "true"
                 :onclick "event.preventDefault(); event.stopPropagation(); return false;"}
          (:board-title link)]
         (str " • " (:url link))]
        (:url link))]]]
   [:div {:class ["flex" "items-center" "gap-2" "flex-shrink-0"]}
    [:div {:onclick "event.stopPropagation()"
           :hx-patch (ext/route router ::r/toggle-link-favorite {:path {:link-id (:id link)}})
           :hx-headers (ext/csrf-token-json)
           :hx-push-url "false"
           :hx-target (str "#favorite-link-icon-" (:id link))}
     (favorite-link-icon link)]
    (c/dropdown-menu
      {:trigger-icon icons/menu
       :items [(c/modal
                 {:open-btn-text [:div {:class ["w-full" "px-4" "py-2" "text-left" "text-sm" "text-gray-700" "hover:text-blue-500" "flex" "items-center" "gap-3" "cursor-pointer"]}
                                  [:div {:class ["flex-shrink-0"]} (icons/edit)]
                                  [:span "Edit"]]
                  :title "Edit link"
                  :submit-btn-title "Save changes"
                  :form-fields (link-edit-form-fields request {:link link
                                                               :boards boards})
                  :form-attrs {:hx-put (ext/route
                                         router
                                         ::r/link-details
                                         {:path {:link-id (:id link)}})
                               :hx-headers (ext/csrf-token-json)
                               :hx-target (str "#link-" (:id link))
                               :hx-swap "outerHTML"
                               :hx-target-error "#link-edit-form-fields"}})
               [:div {:class ["w-full" "px-4" "py-2" "text-left" "text-sm" "text-gray-700" "hover:text-blue-500" "flex" "items-center" "gap-3" "cursor-pointer"]
                      :onclick (str "navigator.clipboard.writeText('" (:url link) "').then(() => { showToast('Link copied to clipboard!'); }).catch(() => { const textArea = document.createElement('textarea'); textArea.value = '" (:url link) "'; document.body.appendChild(textArea); textArea.select(); document.execCommand('copy'); document.body.removeChild(textArea); showToast('Link copied to clipboard!'); });")}
                [:div {:class ["flex-shrink-0"]} icons/copy]
                [:span "Copy Link"]]
               (c/modal
                 {:open-btn-text [:div {:class ["w-full" "px-4" "py-2" "text-left" "text-sm" "text-gray-700" "hover:text-blue-500" "flex" "items-center" "gap-3" "cursor-pointer"]}
                                  [:div {:class ["flex-shrink-0"]} icons/bin]
                                  [:span "Delete"]]
                  :title "Delete link"
                  :submit-btn-title "Confirm"
                  :form-fields [:div
                                [:p {:class ["text-md text-gray-600" "mb-2"]}
                                 "Are you sure you want to delete following link?"]
                                [:b {:class ["text-gray-900" "font-semibold" "line-clamp-3"]}
                                 (or (:title link) (:url link))]]
                  :form-attrs {:hx-delete (ext/route
                                            router
                                            ::r/link-details
                                            {:path {:link-id (:id link)}})
                               :hx-headers (ext/csrf-token-json)
                               :hx-target (str "#link-" (:id link))
                               :hx-swap "delete"}})]})]])

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

(defn empty-links
  []
  [:div {:id "empty-links"
         :class ["flex" "flex-col" "items-center" "justify-center" "py-12" "px-4"]}
   [:div {:class ["w-16" "h-16" "rounded-full" "bg-gray-100" "flex" "items-center" "justify-center" "mb-4"]}
    [:svg {:class ["w-8" "h-8" "text-gray-400"]
           :fill "none"
           :stroke "currentColor"
           :viewBox "0 0 24 24"
           :stroke-width "1.5"}
     [:path {:stroke-linecap "round"
             :stroke-linejoin "round"
             :d "M17.593 3.322c1.1.128 1.907 1.077 1.907 2.185V21L12 17.25 4.5 21V5.507c0-1.108.806-2.057 1.907-2.185a48.507 48.507 0 0 1 11.186 0Z"}]]]
   [:h3 {:class ["text-lg" "font-medium" "text-gray-900" "mb-2"]} "No bookmarks yet"]
   [:p {:class ["text-gray-500" "text-center" "mb-4" "max-w-sm"]}
    "Start building your collection by adding your first link"]])

(defn board-view
  [{router :reitit.core/router
    :as request} {:keys [board links link-count has-more? route page search-term boards]}]
  [:div {:class ["flex-1" "px-4"]}
   ; Title, back button and add link button
   [:div {:class ["flex" "justify-between" "items-center" "mb-4"]}
    [:div {:class ["flex" "items-center" "gap-2"]}
     [:a {:class ["text-blue-500" "hover:text-blue-600"]
          :hx-get (ext/route router ::r/home-page)
          :hx-target "#body"
          :hx-push-url "true"}
      icons/chevron-left]
     [:h2 {:class ["text-2xl" "font-bold"]} (:title board)]]
    [:div {:class ["flex" "items-center" "gap-2"]}
     [:span {:class ["bg-gray-100" "text-gray-600" "px-2" "py-1" "rounded-full" "text-sm" "font-medium"]}
      (str link-count " " (if (= link-count 1) "link" "links"))]
     (c/modal
       {:open-btn-text [:div.ml-2.text-gray-500.hover:text-gray-700.cursor-pointer
                        (icons/edit)]
        :title "Edit board"
        :submit-btn-title "Save changes"
        :form-fields (board-edit-form-fields request {:board board})
        :form-attrs {:hx-put (ext/route router ::r/board-details {:path {:id (:id board)}})
                     :hx-headers (ext/csrf-token-json)
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
        :form-attrs {:hx-delete (ext/route router ::r/board-details {:path {:id (:id board)}})
                     :hx-headers (ext/csrf-token-json)}})]]
   (list
     (c/search-bar {:search-term search-term
                    :route route})
     [:div
      {:id "link-list"
       :class ["flex-1"]}
      (if (seq links)
        (c/paginated-links
          links
          has-more?
          route
          page
          (fn [link]
            (link-list-item {:router router
                             :request request
                             :link link
                             :boards boards})))
        (empty-links))])])

(defn board-pagination-view
  [{router :reitit.core/router
    :as request} {:keys [links has-more? route page boards]}]
  ; Only render new links + infinite scroll trigger for pagination requests
  (c/paginated-links
    links
    has-more?
    route
    page
    (fn [link]
      (link-list-item {:router router
                       :request request
                       :link link
                       :boards boards}))))

(defn all-links-view
  [{router :reitit.core/router
    :as request} {:keys [links link-count has-more? route page search-term boards]}]
  [:div {:class ["flex-1" "px-4"]}
   ; Title, back button and add link button
   [:div {:class ["flex" "justify-between" "items-center" "mb-4"]}
    [:div {:class ["flex" "items-center" "gap-2"]}
     [:a {:class ["text-blue-500" "hover:text-blue-600"]
          :hx-get (ext/route router ::r/home-page)
          :hx-target "#body"
          :hx-push-url "true"}
      icons/chevron-left]
     [:h2 {:class ["text-2xl" "font-bold"]} "All Links"]]
    [:div {:class ["flex" "items-center" "gap-2"]}
     [:span {:class ["bg-gray-100" "text-gray-600" "px-2" "py-1" "rounded-full" "text-sm" "font-medium"]}
      (str link-count " " (if (= link-count 1) "link" "links"))]]]
   (list
     (c/search-bar {:search-term search-term
                    :route route})
     [:div
      {:id "link-list"
       :class ["flex-1"]}
      (if (seq links)
        (c/paginated-links
          links
          has-more?
          route
          page
          (fn [link]
            (link-list-item {:router router
                             :request request
                             :link link
                             :show-board? true
                             :boards boards})))
        (empty-links))])])

(defn all-links-pagination-view
  [{router :reitit.core/router
    :as request} {:keys [links has-more? route page boards]}]
  ; Only render new links + infinite scroll trigger for pagination requests
  (c/paginated-links
    links
    has-more?
    route
    page
    (fn [link]
      (link-list-item {:router router
                       :request request
                       :link link
                       :show-board? true
                       :boards boards}))))
