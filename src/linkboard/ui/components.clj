(ns linkboard.ui.components
  (:require [clojure.string :as str]
            [linkboard.routes :as-alias r]
            [linkboard.ui.icons :as icons]
            [manifest-edn.core :as manifest]
            [reitit-extras.core :as ext]))

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
       (ext/csrf-token-html)
       form-fields]]
     [:div
      {:class ["flex" "flex-row" "justify-end" "space-x-2"]}
      [:button
       {:class ["inline-flex" "items-center" "justify-center" "h-10" "px-4" "py-2"
                "text-sm" "font-medium" "transition-colors" "border" "rounded-md" "cursor-pointer"
                "focus:outline-hidden" "focus:ring-2" "focus:ring-neutral-100" "focus:ring-offset-2"]
        :x-on:click "modalOpen=false"
        :type "button"} "Cancel"]
      [:button
       {:class ["inline-flex" "items-center" "justify-center" "px-4" "py-2" "cursor-pointer"
                "bg-blue-600" "text-white" "rounded-lg" "hover:bg-blue-700" "transition-colors"]
        :autofocus true
        ;:x-on:click "modalOpen=false"
        :type "submit"}
       (or submit-btn-title "Save")]]]]])

(defn login-form-fields
  [request]
  (let [errors (get-in request [:errors :humanized :account-number])]
    [:div
     {:id "login-form-fields"}
     [:label {:class ["text-md" "font-medium" "text-gray-600" "block" "mb-2"]} "Enter your account number"]
     [:input {:type "password"
              :name "account-number"
              :value (get-in request [:parameters :form :account-number] nil)
              :autofocus true
              :class (concat ["flex" "w-full" "h-10" "px-3" "py-2" "text-sm"
                              "bg-white" "border" "rounded-md" "border-neutral-300"
                              "ring-offset-background" "placeholder:text-neutral-500"
                              "focus:border-neutral-300" "focus:outline-hidden"
                              "focus:ring-2" "focus:ring-offset-2" "focus:ring-neutral-400"
                              "disabled:cursor-not-allowed" "disabled:opacity-50"]
                             (when (seq errors)
                               ["border-red-500" "focus:border-red-500" "focus:ring-red-500"]))}]
     (for [error errors]
       [:p {:class ["text-red-500" "text-sm" "mt-1"]} (str/capitalize error)])]))

(defn- login-modal
  [request]
  (modal
    {:title "Login"
     :open-btn-text [:button
                     {:class ["p-4" "text-blue-500" "text-lg" "cursor-pointer"]
                      :x-on:click "modalOpen = true"}
                     "Login"]
     :submit-btn-title "Login"
     :form-attrs {:hx-post (ext/get-route (:reitit.core/router request) ::r/login)
                  :hx-target "#login-form-fields"}
     :form-fields (login-form-fields request)}))

(defn- create-account-modal
  [request]
  (modal
    {:title "Create Account"
     :open-btn-text [:button
                     {:class ["text-blue-500" "text-lg" "cursor-pointer"]
                      :x-on:click "modalOpen = true; accountId = generateAccountId()"}
                     "Register"]
     :submit-btn-title "Create Account"
     :form-attrs {:hx-post (ext/get-route (:reitit.core/router request) ::r/create-account)
                  :hx-target "#content"}
     :form-fields [:div
                   [:div {:class ["mb-4"]}
                    [:label {:class ["text-md" "font-medium" "text-gray-600" "block" "mb-2"]} "Your Account number"]
                    [:div {:x-data "{copied: false}"
                           :class ["bg-gray-100" "p-3" "rounded-lg" "font-mono" "text-lg" "text-center" "cursor-pointer"
                                   "flex" "items-center" "justify-center" "gap-2"]
                           :x-on:click "navigator.clipboard.writeText($el.textContent); copied = true; setTimeout(() => copied = false, 1000)"}
                     [:span {:x-text "accountId"}]
                     [:input {:type "hidden"
                              :name "account-number"
                              :x-model "accountId"}]
                     [:div {:x-show "copied"
                            :x-transition:enter "transform ease-out duration-300"
                            :x-transition:enter-start "opacity-0 translate-y-2"
                            :x-transition:enter-end "opacity-100 translate-y-0"
                            :x-transition:leave "transition ease-in duration-100"
                            :x-transition:leave-start "opacity-100"
                            :x-transition:leave-end "opacity-0"
                            :class ["text-green-500" "absolute" "ml-76"]}
                      "✓"]]
                    [:p {:class ["text-sm" "text-amber-500" "mt-2" "text-left" "font-medium"]}
                     "⚠️ This account number is shown only once. Please store it safely - you cannot restore your account if it's lost."]]]}))

(defn base
  "Base component for html page."
  [{user :identity
    router :reitit.core/router
    :as request}
   content]
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
       (if user
         [:div
          {:class ["flex" "items-center"]}
          [:button
           {:class ["p-4" "text-blue-500" "text-lg" "cursor-pointer"]
            :hx-post (ext/get-route router ::r/logout)
            :hx-headers (ext/csrf-token-json)}
           "Logout"]
          [:button
           {:class ["text-blue-500" "text-lg" "cursor-pointer"]}
           "Account"]]
         [:div
          {:x-data "{ modalOpen: false, accountId: '' }"
           :class ["flex" "items-center"]}
          (login-modal request)
          (create-account-modal request)])]]

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
              :defer true}]
    [:script {:type "text/javascript"
              :src (manifest/asset "js/utils.js")}]]])

(defn error-page
  [request text]
  (base
    request
    [:div {:class ["mt-56"]}
     [:div {:class ["mx-auto" "text-center"]}
      [:h1 {:class ["text-5xl"]} text]]]))

(defn search-bar
  []
  [:div {:class ["pb-4"]}
   [:div {:class ["bg-gray-200" "rounded-lg" "flex" "items-center" "px-4" "py-2"]}
    [:div {:class ["mr-2"]} icons/search]
    [:input {:class ["bg-transparent" "flex-1" "outline-hidden" "text-gray-700"]
             :type "text"
             :placeholder "Search"}]]])
