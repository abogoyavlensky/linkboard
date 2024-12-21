(ns linkboard.icons)

(defn- base-svg
  [{:keys [path color]}]
  [:svg.size-6.hover:text-blue-500.cursor-pointer
   {:class [(or color "text-gray-500")]
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

(def plus-circle
  (base-svg
    {:color "text-blue-500"
     :path {:stroke-linecap "round"
            :stroke-linejoin "round"
            :d "M12 9v6m3-3H9m12 0a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z"}}))

(def plus
  (base-svg
    {:color "text-blue-500"
     :path {:stroke-linecap "round"
            :stroke-linejoin "round"
            :d "M12 4.5v15m7.5-7.5h-15"}}))
