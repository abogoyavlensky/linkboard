(ns linkboard.utils.url
  (:require [clojure.string :as str]
            [hickory.core :as hickory]
            [hickory.select :as s])
  (:import (java.io ByteArrayOutputStream)
           (java.net HttpURLConnection URI URL)
           (java.security SecureRandom)
           (java.security.cert X509Certificate)
           (javax.net.ssl HttpsURLConnection SSLContext TrustManager X509TrustManager HostnameVerifier)))

(def ^:private max-download-bytes
  "Maximum number of bytes to download (1MB)"
  (* 1024 1024))

(defn get-domain-from-url
  "Extract domain from URL for fallback metadata."
  [url]
  (try
    (let [uri (URI. url)
          host (.getHost uri)]
      (if (str/blank? host)
        url
        (let [domain (str/replace host #"^www\." "")]
          domain)))
    (catch Exception _
      (or
        (second (re-find #"^(?:https?://)?(?:www\.)?([^/]+)" url))
        url))))

(defn- setup-trust-all-certs
  "Configure connection to accept all SSL certificates to avoid failures."
  []
  (let [trust-all-certs (into-array TrustManager
                                    [(proxy [X509TrustManager] []
                                       (getAcceptedIssuers [] (make-array X509Certificate 0))
                                       (checkClientTrusted [_ _])
                                       (checkServerTrusted [_ _]))])
        ssl-context (SSLContext/getInstance "TLS")]
    (.init ssl-context nil trust-all-certs (SecureRandom.))
    (HttpsURLConnection/setDefaultSSLSocketFactory (.getSocketFactory ssl-context))
    (HttpsURLConnection/setDefaultHostnameVerifier
      (proxy [HostnameVerifier] []
        (verify [_ _] true)))))

(defn- fetch-url-limited
  "Fetch URL content with a size limit to prevent downloading large files."
  [url-str]
  (try
    (setup-trust-all-certs)
    (let [url (URL. url-str)
          conn ^HttpURLConnection (.openConnection url)]
      (.setRequestProperty conn "User-Agent" "Mozilla/5.0 (compatible; LinkBoard/1.0)")
      (.setConnectTimeout conn 5000)
      (.setReadTimeout conn 5000)
      (when (.getHeaderField conn "Content-Type")
        (let [content-type (.toLowerCase (.getHeaderField conn "Content-Type"))]
          (when-not (str/includes? content-type "text/html")
            ;; If not HTML, don't download
            (throw (ex-info "Not an HTML page" {:url url-str
                                                :content-type content-type})))))

      ;; Read with size limit
      (with-open [is (.getInputStream conn)
                  bos (ByteArrayOutputStream.)]
        (let [buffer (byte-array 4096)
              result (loop [total-bytes 0]
                       (let [bytes-read (.read is buffer)]
                         (cond
                           ;; EOF
                           (neg? bytes-read)
                           {:html (.toString bos "UTF-8")
                            :content-type (.getHeaderField conn "Content-Type")}

                           ;; Size limit reached
                           (> (+ total-bytes bytes-read) max-download-bytes)
                           (do
                             (.write bos buffer 0 (- max-download-bytes total-bytes))
                             {:html (.toString bos "UTF-8")
                              :truncated true
                              :content-type (.getHeaderField conn "Content-Type")})

                           ;; Continue reading
                           :else
                           (do
                             (.write bos buffer 0 bytes-read)
                             (recur (+ total-bytes bytes-read))))))]
          result)))
    (catch Exception e
      {:error (str "Error fetching URL: " (.getMessage e))
       :url url-str})))

(defn- normalize-url
  "Normalize URL for favicon handling."
  [base-url path]
  (cond
    ;; Already absolute URL
    (re-find #"^https?://" path) path

    ;; Protocol-relative URL
    (re-find #"^//" path) (str (if (str/starts-with? base-url "https") "https:" "http:") path)

    ;; Root-relative URL
    (str/starts-with? path "/")
    (let [url-obj (URL. base-url)
          base (str (.getProtocol url-obj) "://" (.getHost url-obj))]
      (str base path))

    ;; Relative URL
    :else
    (let [base-url (if (str/ends-with? base-url "/") base-url (str base-url "/"))]
      (str base-url path))))

(defn- parse-html-metadata
  "Extract title and favicon from HTML content."
  [html url]
  (try
    (let [hickory-doc (-> html hickory/parse hickory/as-hickory)

          ;; Extract title
          title-node (s/select (s/tag :title) hickory-doc)
          title (when (seq title-node)
                  (-> title-node first :content first))

          ;; Define selectors for different favicon types
          icon-selector (s/or
                          (s/and (s/tag :link)
                                 (s/attr :rel #(re-matches #"(?i)^(shortcut )?icon$" %)))
                          (s/and (s/tag :link)
                                 (s/attr :rel #(re-matches #"(?i)^apple-touch-icon(-precomposed)?$" %)))
                          (s/and (s/tag :link)
                                 (s/attr :rel #(= "fluid-icon" %))))

          ;; Find all icon nodes
          icon-nodes (s/select icon-selector hickory-doc)

          ;; Extract href from the first icon found
          icon-href (when (seq icon-nodes)
                      (get-in (first icon-nodes) [:attrs :href]))

          ;; Normalize icon URL or default to /favicon.ico
          favicon-url (if (and icon-href (not (str/blank? icon-href)))
                        (normalize-url url icon-href)
                        (normalize-url url "/favicon.ico"))]

      {:title (if (str/blank? title)
                (get-domain-from-url url)
                title)
       :icon favicon-url})
    (catch Exception e
      {:error (str "Error parsing HTML: " (.getMessage e))
       :url url})))

(defn fetch-page-metadata
  "Fetch a webpage and extract title and favicon.
   Returns a map with :title and :icon. If fetching fails,
   returns domain name as title and empty string as icon."
  [url]
  (if (str/blank? url)
    {:title ""
     :icon ""}
    (let [normalized-url (if (re-find #"^https?://" url)
                           url
                           (str "https://" url))
          result (fetch-url-limited normalized-url)]
      (if (:error result)
        ;; Return domain name as fallback title when fetching fails
        {:title (get-domain-from-url normalized-url)
         :icon ""}
        ;; Parse HTML to extract metadata
        (parse-html-metadata (:html result) normalized-url)))))
