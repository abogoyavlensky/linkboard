(ns linkboard.home.views
  (:require [linkboard.routes :as-alias r]
            [linkboard.ui.components :as c]
            [linkboard.ui.icons :as icons]
            [reitit-extras.core :as ext]))

(defn favorite-icon
  [board]
  [:div
   {:id (str "favorite-icon-" (:id board))}
   (if (:favorite board) icons/star-solid icons/star)])

(defn list-item
  [{:keys [router board]}]
  [:div {:class ["w-full" "bg-white" "rounded-xl" "p-4" "flex" "items-center"
                 "justify-between" "shadow-xs" "mt-2"]
         :id (str "board-" (:id board))}
   [:a {:class ["flex" "items-center" "gap-3" "flex-1" "cursor-pointer"]
        :href (ext/route router ::r/board-details {:path {:id (:id board)}})}
    [:div {:class ["flex" "items-center" "gap-3"]}
     icons/folder
     [:span {:class ["text-lg"]} (:title board)]]]
   [:div {:class ["flex" "items-center" "gap-2"]}
    [:span {:class ["text-gray-500"]} (or (:link-count board) 0)]
    [:div {:class ["flex" "items-center"]
           :onclick "event.stopPropagation()"
           :hx-patch (ext/route router ::r/toggle-board-favorite {:path {:id (:id board)}})
           :hx-headers (ext/csrf-token-json)
           :hx-push-url "false"
           :hx-target (str "#favorite-icon-" (:id board))}
     (favorite-icon board)]
    [:svg {:class ["w-5" "h-5" "text-gray-400" "rotate-180"]
           :viewBox "0 0 24 24"
           :fill "none"
           :stroke "currentColor"}
     [:path {:d "M15 18l-6-6 6-6"
             :stroke-width "2"}]]]])

(defn empty-boards
  []
  [:div {:id "empty-boards"
         :class ["flex" "flex-col" "items-center" "justify-center" "py-12" "px-4"]}
   [:div {:class ["w-16" "h-16" "rounded-full" "bg-gray-100" "flex" "items-center" "justify-center" "mb-4"]}
    [:svg {:class ["w-8" "h-8" "text-gray-400"]
           :fill "none"
           :stroke "currentColor"
           :viewBox "0 0 24 24"}
     [:path {:stroke-linecap "round"
             :stroke-linejoin "round"
             :stroke-width "1.5"
             :d "M2.25 12.75V12A2.25 2.25 0 0 1 4.5 9.75h15A2.25 2.25 0 0 1 21.75 12v.75m-8.69-6.44-2.12-2.12a1.5 1.5 0 0 0-1.061-.44H4.5A2.25 2.25 0 0 0 2.25 6v12a2.25 2.25 0 0 0 2.25 2.25h15A2.25 2.25 0 0 0 21.75 18V9a2.25 2.25 0 0 0-2.25-2.25h-5.379a1.5 1.5 0 0 1-1.06-.44Z"}]]]
   [:h3 {:class ["text-lg" "font-medium" "text-gray-900" "mb-2"]} "No boards yet"]
   [:p {:class ["text-gray-500" "text-center" "mb-4" "max-w-sm"]}
    "Get started by creating your first board to organize your bookmarks"]])

(defn board-list
  [router {:keys [boards has-more? route page]}]
  (if (seq boards)
    (c/paginated-links
      boards
      has-more?
      route
      page
      (fn [board]
        (list-item {:router router
                    :board board})))
    (empty-boards)))

(defn board-form-fields
  [request]
  [:div
   {:id "board-form-fields"}
   (c/form-input {:input-name :title
                  :errors (get-in request [:errors :humanized])
                  :value (get-in request [:parameters :form :title] nil)
                  :text "Title"
                  :attrs {:placeholder "Enter board name"
                          :autofocus true}})])

(defn boards-view
  [{router :reitit.core/router
    :as request} {:keys [boards all-links-count has-more? route page]}]
  [:div {:class ["flex-1" "px-4"]}
   ; TODO: replace with list-item
   [:a {:class ["w-full" "bg-white" "rounded-xl" "mb-4" "p-4" "flex" "items-center" "justify-between" "shadow-xs" "cursor-pointer"]
        :href (ext/route router ::r/links)}
    [:div {:class ["flex" "items-center" "gap-3"]}
     icons/queue-list
     [:span {:class ["text-lg"]} "All Links"]]
    [:div {:class ["flex" "items-center" "gap-2"]}
     [:span {:class ["text-gray-500"]} all-links-count]
     [:svg {:class ["w-5" "h-5" "text-gray-400" "rotate-180"]
            :viewBox "0 0 24 24"
            :fill "none"
            :stroke "currentColor"}
      [:path {:d "M15 18l-6-6 6-6"
              :stroke-width "2"}]]]]
   [:div {:class ["mt-6"]}
    [:div {:class ["flex" "justify-between" "items-center" "mb-4"]}
     [:h2 {:class ["text-gray-500" "text-sm"]} "MY BOARDS"]
     [:div (c/modal
             {:open-btn-text icons/plus
              :title "Create board"
              :form-attrs {:hx-post (ext/route router ::r/board-list)
                           :hx-target "#board-form-fields"
                           :hx-swap "innerHTML"}
              :form-fields (board-form-fields request)})]]
    [:div
     {:id "board-list"}
     (board-list router {:boards boards
                         :has-more? has-more?
                         :route route
                         :page page})]]])

(defn board-pagination-view
  [{router :reitit.core/router} {:keys [boards has-more? route page]}]
  ; Only render new boards + infinite scroll trigger for pagination requests
  (board-list router {:boards boards
                      :has-more? has-more?
                      :route route
                      :page page}))
