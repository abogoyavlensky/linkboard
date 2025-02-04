(ns linkboard.icons)

(defn- base-svg
  [{:keys [path color]}]
  [:svg
   {:class ["size-6" "hover:text-blue-500" "cursor-pointer" (or color "text-gray-500")]
    :xmlns "http://www.w3.org/2000/svg"
    :fill "none"
    :viewBox "0 0 24 24"
    :stroke-width "1.5"
    :stroke "currentColor"}
   [:path path]])

(defn edit
  ([]
   (edit {}))
  ([{:keys [color]}]
   (base-svg
     {:color color
      :path {:stroke-linecap "round"
             :stroke-linejoin "round"
             :d "m16.862 4.487 1.687-1.688a1.875 1.875 0 1 1 2.652 2.652L6.832 19.82a4.5 4.5 0 0 1-1.897 1.13l-2.685.8.8-2.685a4.5 4.5 0 0 1 1.13-1.897L16.863 4.487Zm0 0L19.5 7.125"}})))

(def bin
  (base-svg
    {:path {:stroke-linecap "round"
            :stroke-linejoin "round"
            :d "m14.74 9-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 0 1-2.244 2.077H8.084a2.25 2.25 0 0 1-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 0 0-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 0 1 3.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 0 0-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 0 0-7.5 0"}}))

(def link
  (base-svg
    {:color "text-blue-500"
     :path {:stroke-linecap "round"
            :stroke-linejoin "round"
            :d "M13.5 6H5.25A2.25 2.25 0 0 0 3 8.25v10.5A2.25 2.25 0 0 0 5.25 21h10.5A2.25 2.25 0 0 0 18 18.75V10.5m-10.5 6L21 3m0 0h-5.25M21 3v5.25"}}))

(def folder
  (base-svg
    {:color "text-blue-500"
     :path {:stroke-linecap "round"
            :stroke-linejoin "round"
            :d "M2.25 12.75V12A2.25 2.25 0 0 1 4.5 9.75h15A2.25 2.25 0 0 1 21.75 12v.75m-8.69-6.44-2.12-2.12a1.5 1.5 0 0 0-1.061-.44H4.5A2.25 2.25 0 0 0 2.25 6v12a2.25 2.25 0 0 0 2.25 2.25h15A2.25 2.25 0 0 0 21.75 18V9a2.25 2.25 0 0 0-2.25-2.25h-5.379a1.5 1.5 0 0 1-1.06-.44Z"}}))

(def chevron-left
  (base-svg
    {:path {:stroke-linecap "round"
            :stroke-linejoin "round"
            :d "M15.75 19.5 8.25 12l7.5-7.5"}}))

(def queue-list
  (base-svg
    {:color "text-blue-500"
     :path {:stroke-linecap "round"
            :stroke-linejoin "round"
            :d "M3.75 12h16.5m-16.5 3.75h16.5M3.75 19.5h16.5M5.625 4.5h12.75a1.875 1.875 0 0 1 0 3.75H5.625a1.875 1.875 0 0 1 0-3.75Z"}}))

(def plus
  (base-svg
    {:color "text-blue-500"
     :path {:stroke-linecap "round"
            :stroke-linejoin "round"
            :d "M12 4.5v15m7.5-7.5h-15"}}))

(def plus-circle
  [:svg
   {:class ["text-white" "size-6"]
    :xmlns "http://www.w3.org/2000/svg"
    :fill "none"
    :viewBox "0 0 24 24"
    :stroke-width "1.5"
    :stroke "currentColor"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "M12 9v6m3-3H9m12 0a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z"}]])

(def search
  (base-svg
    {:path {:stroke-linecap "round"
            :stroke-linejoin "round"
            :d "m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.607 10.607Z"}}))

(def github
  [:svg {:class ["size-5" "hover:text-blue-500" "cursor-pointer"]
         :xmlns "http://www.w3.org/2000/svg"
         :width "24"
         :height "24"
         :viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "2"
         :stroke-linecap "round"
         :stroke-linejoin "round"}
   [:path {:d "M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.08-1.25-.27-2.48-1-3.5.28-1.15.28-2.35 0-3.5 0 0-1 0-3 1.5-2.64-.5-5.36-.5-8 0C6 2 5 2 5 2c-.3 1.15-.3 2.35 0 3.5A5.403 5.403 0 0 0 4 9c0 3.5 3 5.5 6 5.5-.39.49-.68 1.05-.85 1.65-.17.6-.22 1.23-.15 1.85v4"}] [:path {:d "M9 18c-4.51 2-5-2-7-2"}]])

(def open-all
  (base-svg
    {:color "text-blue-500"
     :path {:stroke-linecap "round"
            :stroke-linejoin "round"
            :d "m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"}}))

(def menu
  (base-svg
    {:path {:stroke-linecap "round"
            :stroke-linejoin "round"
            :d "M8.625 12a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm0 0H8.25m4.125 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm0 0H12m4.125 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm0 0h-.375M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z"}}))

(def bookmark
  (base-svg
    {:color "text-blue-500"
     :path {:stroke-linecap "round"
            :stroke-linejoin "round"
            :d "M17.593 3.322c1.1.128 1.907 1.077 1.907 2.185V21L12 17.25 4.5 21V5.507c0-1.108.806-2.057 1.907-2.185a48.507 48.507 0 0 1 11.186 0Z"}}))
