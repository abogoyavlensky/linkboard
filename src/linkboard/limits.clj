(ns linkboard.limits
  (:require [ring.util.response :as response]))

; Rate limiting storage - maps IP to {:count N :last-reset timestamp}
(defonce rate-limit-store (atom {}))

(defn get-client-ip
  "Extract client IP from request, handling proxies"
  [request]
  (or (get-in request [:headers "x-forwarded-for"])
      (get-in request [:headers "x-real-ip"])
      (:remote-addr request)
      "unknown"))

(defn clean-expired-entries
  "Remove entries older than window-ms from the store"
  [store window-ms]
  (let [now (System/currentTimeMillis)
        cutoff (- now window-ms)]
    (into {} (filter (fn [[_ {:keys [last-reset]}]]
                       (> last-reset cutoff))
                     store))))

(defn wrap-rate-limit
  "Rate limiting middleware - limits to max-requests per window-ms per IP"
  [handler max-requests window-ms]
  (fn [request]
    (let [client-ip (get-client-ip request)
          now (System/currentTimeMillis)]
      (swap! rate-limit-store clean-expired-entries window-ms)
      (let [current-data (get @rate-limit-store client-ip {:count 0
                                                           :last-reset now})
            time-since-reset (- now (:last-reset current-data))
            should-reset? (>= time-since-reset window-ms)
            new-count (if should-reset? 1 (inc (:count current-data)))
            new-data (if should-reset?
                       {:count 1
                        :last-reset now}
                       (assoc current-data :count new-count))]
        (if (<= new-count max-requests)
          (do
            (swap! rate-limit-store assoc client-ip new-data)
            (handler request))
          ; Rate limit exceeded
          (-> (response/response "Too many requests. Please try again later.")
              (response/status 429)
              (response/header "Retry-After" (str (int (/ (- window-ms time-since-reset) 1000))))))))))
