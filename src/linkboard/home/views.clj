(ns linkboard.home.views
  (:require [linkboard.routes :as-alias r]
            [linkboard.ui.components :as c]
            [linkboard.ui.icons :as icons]
            [reitit-extras.core :as reitit-extras]))

(defn- list-item
  [router board]
  ; TODO: make this component common
  [:a {:class ["w-full" "bg-white" "rounded-xl" "p-4" "flex" "items-center"
               "justify-between" "shadow-xs" "mt-2" "cursor-pointer"]
       :hx-get (reitit-extras/get-route router ::r/board-details {:path {:id (:id board)}})
       :hx-target "#content"
       :hx-push-url "true"}
   [:div {:class ["flex" "items-center" "gap-3"]}
    icons/folder
    [:span {:class ["text-lg"]} (:title board)]]
   [:div {:class ["flex" "items-center" "gap-2"]}
    [:div icons/menu]
    [:span {:class ["text-gray-500"]} (:link-count board)]
    [:svg {:class ["w-5" "h-5" "text-gray-400" "rotate-180"]
           :viewBox "0 0 24 24"
           :fill "none"
           :stroke "currentColor"}
     [:path {:d "M15 18l-6-6 6-6"
             :stroke-width "2"}]]]])

(defn board-list
  [router {:keys [boards]}]
  (list (for [board boards]
          (list-item router board))))

(defn boards-view
  [router {:keys [boards all-links-count]}]
  [:div {:class ["flex-1" "px-4"]}
   ; TODO: replace with list-item
   (c/search-bar)
   [:a {:class ["w-full" "bg-white" "rounded-xl" "mb-4" "p-4" "flex" "items-center" "justify-between" "shadow-xs"]
        :href "#"}
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
             {:open-btn-text (c/button
                               {:content [:div {:class ["flex" "items-center" "gap-1"]}
                                          icons/plus-circle "Add board"]})
              :title "Create board"
              :form-attrs {:hx-post (reitit-extras/get-route router ::r/board-list)
                           :hx-target "#board-list"}
              :form-fields (list
                             [:input
                              {:class ["flex" "w-full" "h-10" "px-3" "py-2" "text-sm"
                                       "bg-white" "border" "rounded-md" "border-neutral-300"
                                       "ring-offset-background" "placeholder:text-neutral-500"
                                       "focus:border-neutral-300" "focus:outline-hidden"
                                       "focus:ring-2" "focus:ring-offset-2" "focus:ring-neutral-400"
                                       "disabled:cursor-not-allowed" "disabled:opacity-50"]
                               :type "text"
                               :name "title"
                               :minlength 1
                               :autofocus true
                               :placeholder "Enter board name"}])})]]
    [:div#board-list
     (board-list router {:boards boards})]]])
