(ns linkboard.ui.components
  (:require [clojure.string :as str]
            [linkboard.routes :as-alias r]
            [linkboard.ui.icons :as icons]
            [linkboard.utils :as utils]
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

(defn form-input
  [{:keys [input-name text value errors attrs]}]
  [:div
   [:label.block.text-sm.font-medium.text-gray-700.mb-1 {:for (name input-name)} text]
   [:input
    (merge
      {:type "text"
       :name (name input-name)
       :class (concat ["flex" "w-full" "h-10" "px-3" "py-2" "text-sm" "mb-3"
                       "bg-white" "border" "rounded-md" "border-neutral-300"
                       "ring-offset-background" "placeholder:text-neutral-500"
                       "focus:border-neutral-300" "focus:outline-hidden"
                       "focus:ring-2" "focus:ring-offset-2" "focus:ring-neutral-400"
                       "disabled:cursor-not-allowed" "disabled:opacity-50"]
                      (when (seq (get errors input-name))
                        ["border-red-500" "focus:border-red-500" "focus:ring-red-500"]))
       :value value}
      attrs)]
   (for [error (get errors input-name)]
     [:p {:class ["text-red-500" "text-sm" "mt-1"]} (str/capitalize error)])])

(defn dropdown-menu
  [{:keys [trigger-icon items]}]
  [:div {:class ["relative" "mt-1"]
         :x-data "{ dropdownOpen: false }"
         :x-on:keydown.escape.window "dropdownOpen = false"
         :x-on:click.away "dropdownOpen = false"}
   ; Menu trigger button
   [:button {:class ["p-1" "text-gray-500" "hover:text-gray-700" "rounded" "cursor-pointer"]
             :x-on:click "dropdownOpen = !dropdownOpen"}
    trigger-icon]

   ; Dropdown menu
   [:div {:class ["absolute" "right-0" "top-8" "mt-1" "w-48" "bg-white" "rounded-lg" "shadow-lg" "border" "border-gray-200" "z-50"]
          :x-cloak ""
          :x-show "dropdownOpen"
          :x-transition:enter "transition ease-out duration-200"
          :x-transition:enter-start "opacity-0 scale-95"
          :x-transition:enter-end "opacity-100 scale-100"
          :x-transition:leave "transition ease-in duration-150"
          :x-transition:leave-start "opacity-100 scale-100"
          :x-transition:leave-end "opacity-0 scale-95"}
    [:div {:class ["py-1"]
           :x-on:click "dropdownOpen = false"}
     (for [item items]
       item)]]])

(defn modal
  [{:keys [title open-btn-text submit-btn-title form-attrs form-fields id-prefix]}]
  [:div.w-auto.h-auto
   {:x-data "{ modalOpen: false }"
    :x-on:keydown.escape.window "modalOpen = false"
    :x-on:modal-close.window "modalOpen = false"
    :hx-on:closeModal "closeModal()"}
   [:button
    {:id (str id-prefix "-modal-btn")
     :x-on:click "modalOpen=true"
     :class "focus:ring-neutral-200/60"}
    open-btn-text]
   [:template
    {:x-teleport "body"}
    [:div
     {:x-cloak ""
      :x-show "modalOpen"
      ; Re-evaluate htmx content when modal is opened
      :x-init "htmx.process($el)"
      :class ["fixed" "inset-0" "flex" "items-center" "justify-center" "z-50" "bg-black/50" "backdrop-blur-xs"]
      :x-on:click "modalOpen=false"}
     [:form
      (merge {:class ["relative" "w-full" "py-6" "bg-white" "border" "shadow-lg" "px-7"
                      "border-neutral-200" "max-w-sm" "md:max-w-md" "rounded-lg"]
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
        (update (ext/csrf-token-html) 1 dissoc :id)
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
         :id (format "%s-submit-btn" id-prefix)
         :type "submit"}
        (or submit-btn-title "Save")
        [:div {:class "htmx-indicator ml-2"} icons/spinner]]]]]]])

(defn login-form-fields
  [request]
  (let [errors (get-in request [:errors :humanized :account-number])]
    [:div
     {:id "login-form-fields"}
     ; TODO: replace with form-input
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
     :id-prefix "login"
     :open-btn-text [:button
                     {:class ["px-4" "py-2" "text-blue-600" "font-semibold" "hover:text-blue-700"
                              "hover:bg-blue-50" "rounded-lg" "transition-all" "duration-200" "cursor-pointer"]
                      :id "login-header-modal-btn"
                      :x-on:click "modalOpen = true"}
                     "Login"]
     :submit-btn-title "Login"
     :form-attrs {:hx-post (ext/route (:reitit.core/router request) ::r/login)
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
     :id-prefix "create-account"
     :open-btn-text [:button
                     {:class ["px-4" "py-2" "text-slate-600" "font-semibold" "hover:text-slate-700"
                              "hover:bg-slate-100" "rounded-lg" "transition-all" "duration-200" "cursor-pointer"
                              "border" "border-slate-300" "hover:border-slate-400"]
                      :x-on:click "modalOpen = true; accountId = generateAccountId()"}
                     "Register"]
     :submit-btn-title "Create Account"
     :form-attrs {:hx-post (ext/route (:reitit.core/router request) ::r/create-account)
                  :hx-target "#body"}
     :form-fields [:div
                   [:div {:class ["mb-4"]}
                    [:label {:class ["text-md" "font-medium" "text-gray-600" "block" "mb-2"]} "Your Account number"]
                    [:div {:x-data "{copied: false}"
                           :class ["flex" "items-center" "gap-3"]}
                     [:div {:class ["bg-gray-100" "p-3" "rounded-lg" "font-mono" "text-lg" "text-center" "cursor-pointer" "flex-1"]
                            :x-on:click "navigator.clipboard.writeText(accountId); copied = true; setTimeout(() => copied = false, 2000)"}
                      [:span {:x-text "accountId"}]
                      [:input {:type "hidden"
                               :name "account-number"
                               :x-model "accountId"}]]

                     ; Copy button / Success indicator  
                     [:div {:class ["p-1" "hover:bg-gray-100" "rounded"]
                            :x-show "!copied"
                            :x-on:click "navigator.clipboard.writeText(accountId); copied = true; setTimeout(() => copied = false, 2000)"}
                      icons/copy]
                     [:div {:class ["p-1" "rounded"]
                            :x-show "copied"
                            :disabled true}
                      icons/check-circle]]
                    [:div {:class ["bg-red-50" "border" "border-red-200" "rounded-lg" "p-4" "mt-4"]}
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
                        [:p "Please store account number safely. It " [:strong "won't be shown again"] " and is required for login."]]]]]
                    [:div {:class ["bg-green-50" "border" "border-green-200" "rounded-lg" "p-4" "mt-4"]}
                     [:div {:class ["flex" "items-start"]}
                      [:div {:class ["flex-shrink-0"]}
                       [:svg {:class ["h-5" "w-5" "text-green-400"]
                              :viewBox "0 0 20 20"
                              :fill "currentColor"}
                        [:path {:fill-rule "evenodd"
                                :d "M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z"
                                :clip-rule "evenodd"}]]]
                      [:div {:class ["ml-3"]}
                       [:h3 {:class ["text-sm" "font-medium" "text-green-800"]} "Info"]
                       [:div {:class ["mt-2" "text-sm" "text-green-700"]}
                        [:p "Your data from current temporary session will be transferred to your new account."]]]]]]]}))

(defn- help-modal
  []
  [:div.w-auto.h-auto
   {:x-data "{ helpModalOpen: false }"
    :x-on:keydown.escape.window "helpModalOpen = false"
    :x-on:help-modal-open.window "helpModalOpen = true"
    :x-on:modal-close.window "helpModalOpen = false"
    :id "help-modal"}
   [:button
    {:x-on:click "helpModalOpen=true"
     :class ["p-2" "text-gray-500" "hover:text-gray-700" "rounded" "cursor-pointer"]
     :title "Help"}
    icons/question-circle]
   [:template
    {:x-teleport "body"}
    [:div
     {:x-cloak ""
      :x-show "helpModalOpen"
      :class ["fixed" "inset-0" "flex" "items-center" "justify-center" "z-50" "bg-black/50" "backdrop-blur-xs"]
      :x-on:click "helpModalOpen=false"}
     [:div
      {:class ["relative" "w-full" "py-6" "bg-white" "border" "shadow-lg" "px-7"
               "border-neutral-200" "max-w-lg" "md:max-w-xl" "rounded-lg"]
       :x-trap.inert.noscroll "helpModalOpen"
       :x-on:click.stop ""}
      [:div {:class ["flex" "items-center" "justify-between" "pb-3"]}
       [:h3 {:class ["text-lg" "font-semibold"]} "Keyboard Shortcuts"]
       [:div
        {:class ["absolute" "top-0" "right-0" "flex" "items-center" "justify-center"
                 "w-8" "h-8" "mt-5" "mr-5" "text-gray-600" "rounded-full" "hover:text-gray-800" "hover:bg-gray-50"]
         :x-on:click "helpModalOpen=false"}
        [:svg {:class ["w-5" "h-5"]
               :xmlns "http://www.w3.org/2000/svg"
               :fill "none"
               :viewBox "0 0 24 24"
               :stroke-width "1.5"
               :stroke "currentColor"}
         [:path {:stroke-linecap "round"
                 :stroke-linejoin "round"
                 :d "M6 18L18 6M6 6l12 12"}]]]]
      [:div {:class ["space-y-3" "text-sm"]}
       [:div {:class ["flex" "justify-between" "items-center" "py-1"]}
        [:span "Add Link"]
        [:kbd {:class ["px-2" "py-1" "bg-gray-100" "rounded" "font-mono" "text-xs"]} "Cmd/Ctrl + A"]]
       [:div {:class ["flex" "justify-between" "items-center" "py-1"]}
        [:span "Create Board"]
        [:kbd {:class ["px-2" "py-1" "bg-gray-100" "rounded" "font-mono" "text-xs"]} "Cmd/Ctrl + B"]]
       [:div {:class ["flex" "justify-between" "items-center" "py-1"]}
        [:span "Navigate to All Links"]
        [:kbd {:class ["px-2" "py-1" "bg-gray-100" "rounded" "font-mono" "text-xs"]} "Cmd/Ctrl + Shift + L"]]
       [:div {:class ["flex" "justify-between" "items-center" "py-1"]}
        [:span "Focus Search"]
        [:kbd {:class ["px-2" "py-1" "bg-gray-100" "rounded" "font-mono" "text-xs"]} "Cmd/Ctrl + K"]]
       [:div {:class ["flex" "justify-between" "items-center" "py-1"]}
        [:span "Show Help"]
        [:kbd {:class ["px-2" "py-1" "bg-gray-100" "rounded" "font-mono" "text-xs"]} "Cmd/Ctrl + /"]]
       [:div {:class ["flex" "justify-between" "items-center" "py-1"]}
        [:span "Clear Search/Close Modals"]
        [:kbd {:class ["px-2" "py-1" "bg-gray-100" "rounded" "font-mono" "text-xs"]} "ESC"]]]
      [:div {:class ["mt-4" "pt-3" "border-t" "border-gray-200"]}
       [:p {:class ["text-xs" "text-gray-500" "text-center"]}
        "Cmd is for Mac users, Ctrl is for Windows/Linux users"]]]]]])

(defn link-form-fields
  [{:keys [board-id]
    :as request}]
  (let [errors (get-in request [:errors :humanized])]
    [:div
     {:id "link-form-fields"}
     (form-input {:input-name :title
                  :errors errors
                  :value (get-in request [:parameters :form :title] nil)
                  :text "Title (optional)"
                  :attrs {:placeholder "Link title"}})
     (form-input {:input-name :url
                  :errors errors
                  :value (get-in request [:parameters :form :url] nil)
                  :text "Link"
                  :attrs {:placeholder "Link title"
                          :autofocus true}})
     (when board-id
       [:input {:type "hidden"
                :name "board"
                :value board-id}])]))

(defn temporary-session-banner
  "Warning banner for non-registered users."
  []
  [:div
   {:class ["px-4"]}
   [:div
    {:class ["bg-amber-50" "border" "border-amber-200" "px-4" "py-3" "mb-4" "rounded-lg"]}
    [:div {:class ["flex" "items-center" "gap-2"]}
     [:span {:class ["text-amber-800" "text-sm"]} "⚠️"]
     [:p {:class ["text-amber-800" "text-sm"]}
      "Using temporary session. Register account to keep your data permanently."]]]])

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
    :x-data ""
    :x-on:keydown.window "if(($event.ctrlKey || $event.metaKey) && $event.key === 'a') { const addButton = document.querySelector('footer button[x-on\\\\:click*=\"modalOpen=true\"]'); if(addButton) { window.dispatchEvent(new CustomEvent('modal-close')); addButton.click(); $event.preventDefault(); } } else if(($event.ctrlKey || $event.metaKey) && $event.key === 'b' && window.location.pathname === '/') { const createBoardButton = Array.from(document.querySelectorAll('button[x-on\\\\:click*=\"modalOpen=true\"]')).find(btn => btn.querySelector('svg') && !btn.closest('footer')); if(createBoardButton) { window.dispatchEvent(new CustomEvent('modal-close')); createBoardButton.click(); $event.preventDefault(); } } else if(($event.ctrlKey || $event.metaKey) && $event.shiftKey && $event.code === 'KeyL') { window.dispatchEvent(new CustomEvent('modal-close')); window.location.href = '/links'; $event.preventDefault(); }"
    :x-on:keydown.cmd.slash.window "window.dispatchEvent(new CustomEvent('modal-close')); window.dispatchEvent(new CustomEvent('help-modal-open')); $event.preventDefault();"
    :x-on:keydown.ctrl.slash.window "window.dispatchEvent(new CustomEvent('modal-close')); window.dispatchEvent(new CustomEvent('help-modal-open')); $event.preventDefault();"
    :hx-on:show-unexpected-error-toast "showToast('Unexpected error occurred. Please try again later.', 'error')"
    :hx-on:show-registration-toast "showToast('Account created successfully! Welcome to Linkboard.')"
    :hx-on:show-board-creation-toast "showToast('Board created successfully!')"
    :hx-on:show-board-edit-toast "showToast('Board updated successfully!')"
    :hx-on:show-board-deletion-toast "showToast('Board deleted successfully!')"
    :hx-on:show-link-creation-toast "showToast('Link added successfully!')"
    :hx-on:show-link-edit-toast "showToast('Link updated successfully!')"
    :hx-on:show-link-deletion-toast "showToast('Link deleted successfully!')"
    :hx-on:show-link-favorite-added-toast "showToast('Link added to favorite!')"
    :hx-on:show-link-favorite-removed-toast "showToast('Link removed from favorite!')"
    :hx-on:show-board-favorite-added-toast "showToast('Board added to favorite!')"
    :hx-on:show-board-favorite-removed-toast "showToast('Board removed from favorite!')"
    :hx-on:show-board-limit-reached-toast "showToast('Board limit reached. You can have up to 50 boards.', 'error')"
    :hx-on:show-link-limit-reached-toast "showToast('Link limit reached. You can have up to 5000 links.', 'error')"
    :hx-on:show-rate-limit-toast "showToast('Too many requests. Please try again later.', 'error')"}
   [:div
    {:class ["h-screen" "flex" "flex-col" "max-w-4xl" "mx-auto"]}
    [:header
     {:class ["px-6" "py-5" "mb-6" "flex" "justify-between" "items-center" "backdrop-blur-sm"]}
     [:div {:class ["flex" "flex-col" "gap-2"]}
      [:a
       {:href (ext/route router ::r/home-page)
        :class ["group" "transition-all" "duration-200"]}
       [:h1 {:class ["text-3xl" "md:text-4xl" "font-extrabold" "text-slate-800"
                     "cursor-pointer" "group-hover:text-blue-600" "transition-colors" "duration-200"]}
        "Linkboard"]]
      [:div {:class ["flex" "items-center" "gap-3" "text-slate-500"]}
       [:p {:class ["text-sm" "md:text-base" "font-medium"]} "Personal bookmark manager"]
       [:a
        {:href PROJECT-GITHUB-LINK
         :target "_blank"
         :class ["p-1" "rounded-md" "hover:bg-slate-100" "transition-colors" "duration-200"]}
        icons/github]]]
     [:div {:class ["flex" "items-center"]}
      (if user
        [:a
         {:class ["px-4" "py-2" "text-blue-600" "font-semibold" "hover:text-blue-700"
                  "hover:bg-blue-50" "rounded-lg" "transition-all" "duration-200" "cursor-pointer"]
          :href (ext/route router ::r/account)}
         "Account"]
        [:div
         {:x-data "{ modalOpen: false, accountId: '' }"
          :class ["flex" "items-center" "gap-3"]}
         (login-modal request)
         (create-account-modal request)])]]

    (when-not user
      (temporary-session-banner))

    [:div
     {:id "content"
      :hx-history-elt true
      :class ["pb-20"]}
     content]
    (toast-container)]

   ; Fixed footer with Help and Add Link buttons
   [:footer
    {:class ["fixed" "bottom-0" "left-1/2" "transform" "-translate-x-1/2" "max-w-4xl"
             "w-full" "backdrop-blur-sm" "border-t" "border-gray-200/50" "px-4" "py-3"]}
    [:div {:class ["flex" "justify-between" "items-center" "mb-2"]}
     (help-modal)
     (modal
       {:open-btn-text (button {:content [:div {:class ["flex" "items-center" "gap-1"]}
                                          icons/plus-circle "Add link"]})
        :title "Add link"
        :id-prefix "add-link"
        :form-attrs {:hx-post (ext/route router ::r/links)
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
      [:h1 {:class ["text-5xl"]} text]
      [:a {:href "/"
           :class ["inline-block" "bg-blue-600" "text-white" "px-4" "py-2" "rounded-md" "hover:bg-blue-700" "transition" "mt-4"]}
       "Go to Home Page"]]]))

(defn search-bar
  [{:keys [search-term route]}]
  (let [base-route (first (str/split route #"\?"))]
    [:div {:class ["pb-4"]
           :x-data ""
           :x-on:keydown.window "if(($event.ctrlKey || $event.metaKey) && $event.key === 'k') { $refs.search.focus(); $event.preventDefault(); } else if($event.key === 'Escape' && document.activeElement === $refs.search) { $refs.search.value = ''; $refs.search.dispatchEvent(new Event('input', { bubbles: true })); }"}
     [:form {:class ["bg-gray-200" "rounded-lg" "flex" "items-center" "px-4" "py-2"]
             :hx-get base-route
             :hx-trigger "input changed delay:500ms, search"
             :hx-target "#link-list"
             :hx-swap "outerHTML"
             :hx-push-url "true"
             :method "get"}
      [:div {:class ["mr-2"]} icons/search]
      [:input {:class ["bg-transparent" "flex-1" "outline-hidden" "text-gray-700"]
               :type "text"
               :name "q"
               :x-ref "search"
               :autofocus true
               :value (or search-term nil)
               :placeholder "Search..."
               :x-init "if($el.value) { $el.setSelectionRange($el.value.length, $el.value.length); }"}]
      (when (and search-term (not (str/blank? search-term)))
        [:button {:type "button"
                  :class ["ml-2" "text-gray-500" "hover:text-gray-700" "cursor-pointer"]
                  :x-on:click "$refs.search.value = ''; $refs.search.dispatchEvent(new Event('input', { bubbles: true })); $refs.search.focus();"}
         icons/x-mark])]]))

(defn infinite-scroll-trigger
  "Creates an HTMX infinite scroll trigger element.
   
   Args:
     route - The route URL to call for the next page (may include existing query params)
     next-page - The page number to load next
   
   Returns:
     Hiccup markup for the infinite scroll trigger"
  [route next-page]
  (let [separator (if (str/includes? route "?") "&" "?")
        pagination-url (str route separator "page=" next-page)]
    [:div {:id (str "page-" next-page "-trigger")
           :hx-trigger "revealed"
           :hx-get pagination-url
           :hx-swap "outerHTML"
           :class ["p-4" "text-center" "text-gray-500" "text-sm"]}
     "Loading more links..."]))

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

(defn back-button
  [request]
  [:a {:class ["text-blue-500" "hover:text-blue-600"]
       :href (utils/back-url request)}
   icons/chevron-left])
