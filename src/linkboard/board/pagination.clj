(ns linkboard.board.pagination
  (:require [linkboard.ui.components :as c]))

(def default-page-size 25)

(defn add-pagination
  "Adds LIMIT and OFFSET clauses to a HoneySQL query map for pagination.
   
   Args:
     query - HoneySQL query map
     page - Page number (1-based)
     page-size - Number of items per page (defaults to 25)
   
   Returns:
     Updated query map with :limit and :offset"
  ([query page]
   (add-pagination query page default-page-size))
  ([query page page-size]
   (assoc query
          :limit page-size
          :offset (* (dec page) page-size))))

(defn has-more-pages?
  "Determines if more pages exist based on total count and current page.
   
   Args:
     total-count - Total number of items
     page - Current page number (1-based)  
     page-size - Number of items per page (defaults to 25)
   
   Returns:
     Boolean indicating if more pages exist"
  ([total-count page]
   (has-more-pages? total-count page default-page-size))
  ([total-count page page-size]
   (> total-count (* page page-size))))

(defn get-page-param
  "Extracts page parameter from request, defaulting to 1 if not present or invalid.
   
   Args:
     request - Ring request map
   
   Returns:
     Page number as positive integer (minimum 1)"
  [request]
  (let [page (get-in request [:parameters :query :page])]
    (if (and page (pos-int? page))
      page
      1)))

(defn pagination-request?
  "Determines if this is a pagination request (HTMX request with page > 1).
   
   Args:
     request - Ring request map
   
   Returns:
     Boolean indicating if this is a pagination request"
  [request]
  (and (c/hx-request? request)
       (> (get-page-param request) 1)))