(ns linkboard.ui.components
  (:require [linkboard.routes :as-alias r]
            [linkboard.ui.icons :as icons]
            [manifest-edn.core :as manifest]
            [reitit-extras.core :as reitit-extras]))

(def ^:const PROJECT-GITHUB-LINK "https://github.com/abogoyavlensky/linkboard")

(defn hx-request?
  [{:keys [headers]}]
  (= "true" (get headers "hx-request")))

(defn button
  [{:keys [content]}]
  [:div
   {:class ["inline-flex" "items-center" "px-4" "py-2" "bg-blue-600" "text-white"
            "rounded-lg" "hover:bg-blue-700" "transition-colors" "cursor-pointer"]
    :type "button"}
   content])

(defn base
  "Base component for html page."
  [content]
  [:html
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport"
            :content "width=device-width, initial-scale=1, maximum-scale=1, user-scalable=0"}]
    [:meta {:name "msapplication-TileColor"
            :content "#f9fafb"}]
    [:link {:rel "manifest"
            :href "/assets/manifest.json"}]
    [:link {:rel "icon"
            :href (manifest/asset "images/favicon-1.png")}]
    [:link {:rel "apple-touch-icon"
            :sizes "180x180"
            :href (manifest/asset "images/apple-touch-icon-1.png")}]
    [:link {:type "text/css"
            :href (manifest/asset "css/output.css")
            :rel "stylesheet"}]
    [:title "Linkboard"]]
   [:body
    {:class ["bg-slate-50"]}
    [:div
     {:class ["h-screen" "flex" "flex-col" "max-w-4xl" "mx-auto"]}
     [:div
      {:class ["px-4" "pt-2" "pb-4" "mb-2" "md:mb-4" "flex" "justify-between" "items-center"]}
      [:div
       [:a
        {:hx-get "/"
         :hx-target "#content"
         :hx-push-url "true"}
        [:h1 {:class ["text-3xl" "font-bold" "cursor-pointer"]} "Linkboard"]]
       [:div {:class ["text-gray-400" "flex" "items-center" "gap-2"]}
        [:p "Personal bookmark manager"]
        [:a
         {:href PROJECT-GITHUB-LINK
          :target "_blank"}
         icons/github]]]
      [:div {:class ["flex" "gap-4"]}
       [:a {:class ["text-blue-500" "text-lg"]
            :href "#"} "Sync"]]]
     [:div
      {:id "content"
       :hx-history-elt true
       :class ["pb-12"]}
      content]]
    [:script {:type "text/javascript"
              :src (manifest/asset "js/htmx.min.js")}]
    [:script {:type "text/javascript"
              :src (manifest/asset "js/alpinejs.focus.min.js")
              :defer true}]
    [:script {:type "text/javascript"
              :src (manifest/asset "js/alpinejs.min.js")
              :defer true}]]])

(defn error-page
  [text]
  (base
    [:div {:class ["mt-56"]}
     [:div {:class ["mx-auto" "text-center"]}
      [:h1 {:class ["text-5xl"]} text]]]))

(defn modal
  [{:keys [title open-btn-text submit-btn-title form-attrs form-fields]}]
  [:div.relative.w-auto.h-auto
   {:x-data "{ modalOpen: false }"
    :x-on:keydown.escape.window "modalOpen = false"
    :x-cloak ""}
   [:button
    {:x-on:click "modalOpen=true"
     :class "focus:ring-neutral-200/60"}
    open-btn-text]
   [:div
    {:x-show "modalOpen"
     :x-cloak ""
     :style "display: none;"
     :class ["z-99" "fixed" "top-0" "left-0" "flex" "items-center" "justify-center" "w-screen" "h-screen"]}
    [:div
     {:class ["absolute" "inset-0" "w-full" "h-full" "backdrop-blur-xs" "bg-opacity-70" "bg-black/50"]
      :x-show "modalOpen"
      :x-transition:enter "ease-out duration-300"
      :x-transition:enter-start "opacity-0"
      :x-transition:enter-end "opacity-100"
      :x-transition:leave "ease-in duration-300"
      :x-transition:leave-start "opacity-100"
      :x-transition:leave-end "opacity-0"
      :x-on:click "modalOpen=false"}]
    [:form
     (merge {:class ["relative" "w-full" "py-6" "bg-white" "border" "shadow-lg" "px-7"
                     "border-neutral-200" "max-w-xs" "md:max-w-md" "rounded-lg"]
             :x-show "modalOpen"
             :style "display: none;"
             :x-trap.inert.noscroll "modalOpen"
             :x-transition:enter "ease-out duration-300"
             :x-transition:enter-start "opacity-0 -translate-y-2 sm:scale-95"
             :x-transition:enter-end "opacity-100 translate-y-0 sm:scale-100"
             :x-transition:leave "ease-in duration-200"
             :x-transition:leave-start "opacity-100 translate-y-0 sm:scale-100"
             :x-transition:leave-end "opacity-0 -translate-y-2 sm:scale-95"}
            form-attrs)
     [:div {:class ["flex" "items-center" "justify-between" "pb-3"]}
      [:h3 {:class ["text-lg" "font-semibold"]} title]
      [:div
       {:class ["absolute" "top-0" "right-0" "flex" "items-center" "justify-center"
                "w-8" "h-8" "mt-5" "mr-5" "text-gray-600" "rounded-full" "hover:text-gray-800" "hover:bg-gray-50"]
        :x-on:click "modalOpen=false"}
       [:svg {:class ["w-5" "h-5"]
              :xmlns "http://www.w3.org/2000/svg"
              :fill "none"
              :viewBox "0 0 24 24"
              :stroke-width "1.5"
              :stroke "currentColor"}
        [:path {:stroke-linecap "round"
                :stroke-linejoin "round"
                :d "M6 18L18 6M6 6l12 12"}]]]]
     [:div
      {:class ["relative" "w-auto" "pb-8"]}
      [:div
       {:class ["w-full" "max-w-xs" "mx-auto"]}
       (reitit-extras/csrf-token-html)
       form-fields]]
     [:div
      {:class ["flex" "flex-row" "justify-end" "space-x-2"]}
      [:button
       {:class ["inline-flex" "items-center" "justify-center" "h-10" "px-4" "py-2"
                "text-sm" "font-medium" "transition-colors" "border" "rounded-md"
                "focus:outline-hidden" "focus:ring-2" "focus:ring-neutral-100" "focus:ring-offset-2"]
        :x-on:click "modalOpen=false"
        :type "button"} "Cancel"]
      [:button
       {:class ["inline-flex" "items-center" "justify-center" "px-4" "py-2"
                "bg-blue-600" "text-white" "rounded-lg" "hover:bg-blue-700" "transition-colors"]
        :x-on:click "modalOpen=false"
        :type "submit"}
       (or submit-btn-title "Save")]]]]])

(defn search-bar
  []
  [:div {:class ["pb-4"]}
   [:div {:class ["bg-gray-200" "rounded-lg" "flex" "items-center" "px-4" "py-2"]}
    [:div {:class ["mr-2"]} icons/search]
    [:input {:class ["bg-transparent" "flex-1" "outline-hidden" "text-gray-700"]
             :type "text"
             :placeholder "Search"}]]])
