(ns linkboard.home-page
  (:require [linkboard.components :as components]
            [linkboard.db :as db]
            [linkboard.icons :as icons]
            [reitit-extras.core :as reitit-extras]))

; TODO: change to authenticated user
(def USER-ID 1)

(defn- list-item
  [board]
  ; TODO: make this component common
  [:a {:class ["w-full" "bg-white" "rounded-xl" "p-4" "flex" "items-center" "justify-between" "shadow-xs" "mt-4" "cursor-pointer"]
       :hx-get (format "/boards/%s" (:id board))
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

(defn- board-list
  [{:keys [boards]}]
  (list (for [board boards]
          (list-item board))))

(defn- boards-view
  [{:keys [boards all-links-count]}]
  [:div {:class ["flex-1" "px-4"]}
   ; TODO: replace with list-item
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
    [:div {:class ["flex" "justify-between" "mb-4"]}
     [:h2 {:class ["text-gray-500" "text-sm"]} "MY BOARDS"]
     [:div (components/modal
             {:open-btn-text icons/plus
              :title "Create board"
              :hx-post "/boards"
              :hx-target "#board-list"
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
     (board-list {:boards boards})]]])

(defn home-handler
  {:malli/schema [:=> [:cat :map] :map]}
  [{{:keys [db]} :context
    :as request}]
  (let [all-links-count (->> {:select [[[:count :l.id] :links-count]]
                              :from [[:board :b]]
                              :join [[:link :l] [:= :b.id :l.board-id]]
                              :where [:= :b.user-id USER-ID]}
                          (db/exec-one! db)
                          :links-count)
        ; TODO: add pagination
        boards (db/exec! db {:select [:b.*
                                      [[:count :l.id] :link-count]]
                             :from [[:board :b]]
                             :left-join [[:link :l] [:= :b.id :l.board-id]]
                             :where [:= :b.user-id USER-ID]
                             :group-by [:b.id :b.title]
                             :order-by [[:b.created_at :desc]]})
        page-view (cond-> (boards-view {:boards boards
                                        :all-links-count all-links-count})
                    (not (components/hx-request? request)) components/base)]
    (reitit-extras/render-html page-view)))

(defn create-board-handler
  {:malli/schema [:=> [:cat :map] :map]}
  [{{:keys [db]} :context
    {:keys [form]} :parameters}]
  ; Create a new board
  (->> {:insert-into :board
        :values [{:title (:title form)
                  :user-id USER-ID}]}
    (db/exec-one! db))
  ; Render home page with new board in the list
  (let [boards (db/exec! db {:select [:b.*
                                      [[:count :l.id] :link-count]]
                             :from [[:board :b]]
                             :left-join [[:link :l] [:= :b.id :l.board-id]]
                             :where [:= :b.user-id USER-ID]
                             :group-by [:b.id :b.title]
                             :order-by [[:b.created_at :desc]]})]
    (-> {:boards boards}
      (board-list)
      (reitit-extras/render-html))))
