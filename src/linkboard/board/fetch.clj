(ns linkboard.board.fetch
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [hickory.core :as hickory]
            [hickory.select :as s]
            [lambdaisland.uri :as uri]
            [sentry-clj.tracing :as tracing]))

(def ^:private max-download-bytes
  "Maximum number of bytes to download (32KB)"
  (* 128 1024))

(def ^:private ^:const OPENROUTER-API-URL
  "OpenRouter API endpoint for chat completions"
  "https://openrouter.ai/api/v1/chat/completions")

(def ^:private ^:const MODEL
  "Model to use for link board detection"
  "google/gemma-3-27b-it:free")

(defn get-domain-from-url
  "Extract domain from URL for fallback metadata."
  [url]
  (try
    (let [parsed (uri/uri url)
          host (or (:host parsed) "")]
      (if (str/blank? host)
        url
        (str/replace host #"^www\." "")))
    (catch Exception _
      ; If URL parsing fails, return the original URL
      url)))

(defn- fetch-url-limited
  "Fetch URL content with a size limit to prevent downloading large files."
  [url-str]
  (try
    (let [response (http/get url-str
                             {:socket-timeout 5000
                              :conn-timeout 5000
                              :max-body-length max-download-bytes
                              :headers {"User-Agent" "Mozilla/5.0 (compatible; LinkBoard/1.0)"}
                              :insecure? true ; Accept self-signed certificates
                              :throw-exceptions false})]

      (if (not= 200 (:status response))
        {:error (str "HTTP error: " (:status response))
         :url url-str}
        (let [content-type (get-in response [:headers "content-type"] "")]
          (if-not (str/includes? (str/lower-case content-type) "text/html")
            {:error (str "Not an HTML page: " content-type)
             :url url-str}
            {:html (:body response)
             :content-type content-type}))))
    (catch Exception e
      {:error (str "Error fetching URL: " (.getMessage e))
       :url url-str})))

(defn- get-favicon-url
  "Normalize URL for favicon handling."
  [base-url path]
  (let [url-map (uri/uri base-url)
        favicon-path (:path (uri/uri path))
        favicon-path* (if (str/starts-with? favicon-path "/")
                        favicon-path
                        (str "/" favicon-path))
        result-url (if (and (:host (uri/uri path)) (:scheme (uri/uri path)))
                     path
                     (format "%s://%s%s" (:scheme url-map) (:host url-map) favicon-path*))
        response (http/get result-url
                           {:socket-timeout 5000
                            :conn-timeout 5000
                            :max-body-length max-download-bytes
                            :headers {"User-Agent" "Mozilla/5.0 (compatible; LinkBoard/1.0)"}
                            :insecure? true ; Accept self-signed certificates
                            :throw-exceptions false})]
    (when (= 200 (:status response))
      result-url)))

(defn- parse-html-metadata
  "Extract title and favicon from HTML content."
  [html url]
  (try
    (let [hickory-doc (-> html hickory/parse hickory/as-hickory)

          ; Extract title
          title-node (s/select (s/tag :title) hickory-doc)
          title (when (seq title-node)
                  (-> title-node first :content first))

          ; Extract description from meta tag
          description-selector (s/and (s/tag :meta)
                                      (s/attr :name #(= "description" %)))
          description-node (s/select description-selector hickory-doc)
          description (when (seq description-node)
                        (get-in (first description-node) [:attrs :content]))

          ; Define selectors for different favicon types
          icon-selector (s/or
                          (s/and (s/tag :link)
                                 (s/attr :rel #(re-matches #"(?i)^(shortcut )?icon$" %)))
                          (s/and (s/tag :link)
                                 (s/attr :rel #(re-matches #"(?i)^apple-touch-icon(-precomposed)?$" %))))

          ; Find all icon nodes
          icon-nodes (s/select icon-selector hickory-doc)

          ;; Extract href from the first icon found
          icon-href (when (seq icon-nodes)
                      (get-in (first icon-nodes) [:attrs :href]))

          ;; Normalize icon URL or default to /favicon.ico
          favicon-url (when (and icon-href (not (str/blank? icon-href)))
                        (get-favicon-url url icon-href))]

      {:title (if (str/blank? title)
                (get-domain-from-url url)
                title)
       :icon (or favicon-url "")
       :description description})
    (catch Exception e
      (log/warn "[FETCH] Error parsing HTML:" (.getMessage e))
      {:title (get-domain-from-url url)
       :icon ""
       :description nil})))

(defn fetch-page-metadata
  "Fetch a webpage and extract title and favicon.
   Returns a map with :title and :icon. If fetching fails,
   returns domain name as title and empty string as icon."
  [url]
  (let [normalized-url (if (re-find #"^https?://" url)
                         url
                         (str "https://" url))
        result (tracing/with-start-child-span "fetch.link" url
                 (fetch-url-limited normalized-url))]
    (if (:error result)
      (do
        (log/warn "[FETCH] Error fetching URL:" (:error result))
        ;; Return domain name as fallback title when fetching fails
        {:title (get-domain-from-url normalized-url)
         :icon ""})
      ;; Parse HTML to extract metadata
      (tracing/with-start-child-span "fetch.parse-html" url
        (parse-html-metadata (:html result) normalized-url)))))

(defn detect-board-for-link
  "Detect the best matching board for a link using LLM.
   Returns board ID if a match is found, nil otherwise.
   `metadata` - map with :title and :description from fetch-page-metadata
   `url` - the link URL
   `boards` - list of maps with :id and :title keys"
  [{:keys [metadata url boards options]}]
  (let [api-key (:openrouter-api-key options)]
    (when (and api-key (seq boards))
      (try
        (tracing/with-start-child-span "fetch.detect-board" url
          (let [board-names (str/join ", " (map :title boards))
                prompt (str "Given these boards: " board-names
                            "\nWhich board fits this link best? Respond with board name ONLY.\n"
                            "Title: " (:title metadata) "\n"
                            "Description: " (or (:description metadata) "N/A") "\n"
                            "URL: " url)
                response (http/post OPENROUTER-API-URL
                                    {:socket-timeout 10000
                                     :conn-timeout 5000
                                     :headers {"Authorization" (str "Bearer " api-key)
                                               "Content-Type" "application/json"}
                                     :body (json/encode
                                             {:model MODEL
                                              :messages [{:role "user"
                                                          :content prompt}]})
                                     :throw-exceptions false})
                detected-name (if (= 200 (:status response))
                                (-> (json/decode (:body response) true)
                                    :choices first :message :content
                                    str/trim)
                                (log/warn "[FETCH] Not 200 response while detecting board:"
                                          (:body response)))]
            ; Find board by matching title (case-insensitive)
            (when detected-name
              (->> boards
                   (filter #(= (str/lower-case (:title %))
                               (str/lower-case detected-name)))
                   first
                   :id
                   str))))
        (catch Exception e
          (log/warn "[FETCH] Error detecting board:" (.getMessage e))
          nil)))))
