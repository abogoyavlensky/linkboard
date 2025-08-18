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
  [:div.w-auto.h-auto
   {:x-data "{ modalOpen: false }"
    :x-on:keydown.escape.window "modalOpen = false"
    :x-on:modal-close.window "modalOpen = false"
    :hx-on:closeModal "closeModal()"}
   [:button
    {:x-on:click "modalOpen=true"
     :class "focus:ring-neutral-200/60"}
    open-btn-text]
   [:template
    {:x-teleport "body"}
    [:div
     {:x-cloak ""
      :x-show "modalOpen"
      :class ["fixed" "inset-0" "flex" "items-center" "justify-center" "z-50" "bg-black/50" "backdrop-blur-xs"]
      :x-on:click "modalOpen=false"}
     [:form
      (merge {:class ["relative" "w-full" "py-6" "bg-white" "border" "shadow-lg" "px-7"
                      "border-neutral-200" "max-w-xs" "md:max-w-md" "rounded-lg"]
              :x-trap.inert.noscroll "modalOpen"
              :x-on:click.stop ""}
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
         :type "submit"}
        (or submit-btn-title "Save")
        [:div {:class "htmx-indicator ml-2"} icons/spinner]]]]]]])

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

(defn toast-container
  "Toast notification container component"
  []
  [:div#toast-container
   {:class ["fixed" "bottom-20" "left-1/2" "transform" "-translate-x-1/2" "z-50" "space-y-2"]
    :x-data "{ toasts: [] }"
    :x-on:show-toast.window "
      const toast = { 
        id: Date.now(), 
        message: $event.detail.message, 
        type: $event.detail.type || 'success' 
      };
      toasts.push(toast);
      setTimeout(() => {
        toasts = toasts.filter(t => t.id !== toast.id);
      }, 4000);
    "}
   [:template {:x-for "toast in toasts"
               :key "toast.id"}
    [:div
     {:class ["px-4" "py-3" "rounded-lg" "shadow-lg" "bg-white" "border-2" "min-w-80" "max-w-md"]
      :x-bind:class "{
        'border-green-500 text-gray-800': toast.type === 'success',
        'border-red-500 text-gray-800': toast.type === 'error',
        'border-blue-500 text-gray-800': toast.type === 'info',
        'border-yellow-500 text-gray-800': toast.type === 'warning'
      }"
      :x-transition:enter "transform ease-out duration-300"
      :x-transition:enter-start "opacity-0 translate-y-full"
      :x-transition:enter-end "opacity-100 translate-y-0"
      :x-transition:leave "transition ease-in duration-300"
      :x-transition:leave-start "opacity-100 translate-y-0"
      :x-transition:leave-end "opacity-0 translate-y-full"}
     [:div {:class ["flex" "items-center" "justify-between"]}
      [:div {:class ["flex" "items-center" "gap-2"]}
       [:div {:x-show "toast.type === 'success'"
              :class ["text-green-500"]}
        ; Green check mark SVG
        [:svg {:class ["w-5" "h-5"]
               :fill "currentColor"
               :viewBox "0 0 20 20"}
         [:path {:fill-rule "evenodd"
                 :d "M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z"
                 :clip-rule "evenodd"}]]]
       [:span {:x-text "toast.message"}]]
      [:button
       {:x-on:click "toasts = toasts.filter(t => t.id !== toast.id)"
        :class ["ml-4" "text-gray-500" "hover:text-gray-700"]}
       "×"]]]]])

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
                  :hx-target "#body"}
     :form-fields [:div
                   [:div {:class ["mb-4"]}
                    [:label {:class ["text-md" "font-medium" "text-gray-600" "block" "mb-2"]} "Your Account number"]
                    [:div {:x-data "{copied: false}"
                           :class ["flex" "items-center" "gap-3"]}
                     [:div {:class ["bg-gray-100" "p-3" "rounded-lg" "font-mono" "text-lg" "text-center" "cursor-pointer" "flex-1"]
                            :x-on:click "navigator.clipboard.writeText(accountId); copied = true; setTimeout(() => copied = false, 1000)"}
                      [:span {:x-text "accountId"}]
                      [:input {:type "hidden"
                               :name "account-number"
                               :x-model "accountId"}]]
                     [:div {:class ["flex" "items-center" "justify-center" "w-6" "h-6" "rounded-full" "text-sm" "font-bold"]
                            :x-bind:class "copied ? 'bg-green-500 text-white' : 'bg-transparent'"
                            :x-transition:enter "transform ease-out duration-300"
                            :x-transition:enter-start "opacity-0 scale-0"
                            :x-transition:enter-end "opacity-100 scale-100"
                            :x-transition:leave "transition ease-in duration-200"
                            :x-transition:leave-start "opacity-100 scale-100"
                            :x-transition:leave-end "opacity-0 scale-0"}
                      [:span {:x-show "copied"} "✓"]]]
                    [:p {:class ["text-sm" "text-amber-500" "mt-2" "text-left" "font-medium"]}
                     "⚠️ This account number is shown only once. Please store it safely - you cannot restore your account if it's lost."]]]}))

(defn link-form-fields
  [{:keys [board-id]
    :as request}]
  (let [errors (get-in request [:errors :humanized])]
    [:div
     {:id "link-form-fields"}
     [:input
      {:class (concat ["flex" "w-full" "h-10" "px-3" "py-2" "text-sm"
                       "bg-white" "border" "rounded-md" "border-neutral-300"
                       "ring-offset-background" "placeholder:text-neutral-500"
                       "focus:border-neutral-300" "focus:outline-hidden"
                       "focus:ring-2" "focus:ring-offset-2" "focus:ring-neutral-400"
                       "disabled:cursor-not-allowed" "disabled:opacity-50"]
                      (when (seq (:url errors))
                        ["border-red-500" "focus:border-red-500" "focus:ring-red-500"]))
       :type "text"
       :name "url"
       :value (get-in request [:parameters :form :url] nil)
       :minlength 1
       :autofocus true
       :placeholder "Enter link"}]
     (for [error (:url errors)]
       [:p {:class ["text-red-500" "text-sm" "mt-1"]} (str/capitalize error)])
     (when board-id
       [:input
        {:class (concat ["flex" "w-full" "h-10" "px-3" "py-2" "text-sm"
                         "bg-white" "border" "rounded-md" "border-neutral-300"
                         "ring-offset-background" "placeholder:text-neutral-500"
                         "focus:border-neutral-300" "focus:outline-hidden"
                         "focus:ring-2" "focus:ring-offset-2" "focus:ring-neutral-400"
                         "disabled:cursor-not-allowed" "disabled:opacity-50"]
                        (when (seq (:board errors))
                          ["border-red-500" "focus:border-red-500" "focus:ring-red-500"]))
         :type "hidden"
         :name "board"
         :value board-id}])]))

(defn body
  [{user :identity
    router :reitit.core/router
    :as request}
   content]
  [:body
   {:id "body"
    :hx-ext "response-targets"
    :hx-history-elt true
    :class ["bg-slate-50"]
    :hx-on:show-registration-toast "showToast('Account created successfully! Welcome to Linkboard.')"
    :hx-on:show-board-creation-toast "showToast('Board created successfully!')"
    :hx-on:show-board-edit-toast "showToast('Board updated successfully!')"
    :hx-on:show-board-deletion-toast "showToast('Board deleted successfully!')"
    :hx-on:show-link-creation-toast "showToast('Link added successfully!')"
    :hx-on:show-link-edit-toast "showToast('Link updated successfully!')"
    :hx-on:show-link-deletion-toast "showToast('Link deleted successfully!')"}
   [:div
    {:class ["h-screen" "flex" "flex-col" "max-w-4xl" "mx-auto"]}
    [:div
     {:class ["px-4" "pt-2" "pb-4" "mb-2" "md:mb-4" "flex" "justify-between" "items-center"]}
     [:div
      [:a
       {:hx-get (ext/get-route router ::r/home-page)
        :hx-target "#body"
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
      :class ["pb-20"]}
     content]
    (toast-container)]

   ; Fixed footer with Add Link button
   [:footer
    {:class ["fixed" "bottom-0" "left-1/2" "transform" "-translate-x-1/2" "max-w-4xl"
             "w-full" "backdrop-blur-sm" "border-t" "border-gray-200/50" "pr-4" "py-3"]}
    [:div {:class ["flex" "justify-end" "mb-2"]}
     (modal
       {:open-btn-text (button {:content [:div {:class ["flex" "items-center" "gap-1"]}
                                          icons/plus-circle "Add link"]})
        :title "Add link"
        :form-attrs {:hx-post (ext/get-route router ::r/links)
                     :hx-target "#link-form-fields"
                     :hx-swap "innerHTML"}
        :form-fields (link-form-fields request)})]]

   [:script {:type "text/javascript"
             :src (manifest/asset "js/htmx.min.js")}]
   [:script {:type "text/javascript"
             :src (manifest/asset "js/htmx-ext-response-targets.js")}]
   [:script {:type "text/javascript"
             :src (manifest/asset "js/alpinejs.focus.min.js")
             :defer true}]
   [:script {:type "text/javascript"
             :src (manifest/asset "js/alpinejs.min.js")
             :defer true}]
   [:script {:type "text/javascript"
             :src (manifest/asset "js/utils.js")}]])

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
    [:style "[x-cloak] { display: none !important; }"]
    [:title "Linkboard"]]
   content])

(defn error-page
  [text]
  (base
    [:div {:class ["mt-56"]}
     [:div {:class ["mx-auto" "text-center"]}
      [:h1 {:class ["text-5xl"]} text]]]))

(defn search-bar
  [{:keys [search-term route]}]
  [:div {:class ["pb-4"]
         :x-data ""
         :x-on:keydown.window "if($event.key === '/' || (($event.ctrlKey || $event.metaKey) && $event.key === 'k')) { $refs.search.focus(); $event.preventDefault(); }"}
   [:form {:class ["bg-gray-200" "rounded-lg" "flex" "items-center" "px-4" "py-2"]
           :hx-get route
           :hx-trigger "submit"
           :hx-target "#body"
           :hx-push-url "true"
           :method "get"}
    [:div {:class ["mr-2"]} icons/search]
    [:input {:class ["bg-transparent" "flex-1" "outline-hidden" "text-gray-700"]
             :type "text"
             :name "q"
             :x-ref "search"
             :value (or search-term nil)
             :placeholder "Search"}]]])

(defn infinite-scroll-trigger
  "Creates an HTMX infinite scroll trigger element.
   
   Args:
     route - The route URL to call for the next page
     next-page - The page number to load next
   
   Returns:
     Hiccup markup for the infinite scroll trigger"
  [route next-page]
  [:div {:id (str "page-" next-page "-trigger")
         :hx-trigger "revealed"
         :hx-get (str route "?page=" next-page)
         :hx-swap "outerHTML"
         :class ["p-4" "text-center" "text-gray-500" "text-sm"]}
   "Loading more links..."])

(defn paginated-links
  "Renders a list of links with optional infinite scroll trigger.
   
   Args:
     links - Collection of links to render
     has-more? - Boolean indicating if more pages exist
     route - Base route for pagination (without query params)
     current-page - Current page number
     link-item-fn - Function to render individual link items
   
   Returns:
     Hiccup markup for links + optional trigger"
  [links has-more? route current-page link-item-fn]
  (list
    ;; Render all links
    (for [link links]
      (link-item-fn link))

    ;; Add infinite scroll trigger if more pages exist
    (when has-more?
      (infinite-scroll-trigger route (inc current-page)))))
