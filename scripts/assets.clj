(ns assets
  (:require [clojure.string :as str]
            [babashka.fs :as fs]
            [clj-commons.digest :as digest]
            [babashka.http-client :as http]))

(def ^:private ASSET-DIR-JS "resources/public/js")

(defn hash-asset-file!
  [{:keys [asset-file target-file]}]
  (let [content (slurp asset-file)
        content-hash (digest/md5 content)
        asset-file-name (fs/file-name asset-file)
        [asset-file-name-no-ext asset-file-ext] (fs/split-ext asset-file-name)
        asset-file-name-hashed (format "%s.%s.%s" asset-file-name-no-ext content-hash asset-file-ext)
        asset-file-path-hashed (fs/file (fs/parent asset-file) asset-file-name-hashed)]
    ; create hashed asset file
    (spit asset-file-path-hashed content)
    ; update output asset file name with hash in target file
    (fs/update-file target-file #(str/replace % asset-file-name asset-file-name-hashed))))

(defn hash-asset-file!-NEW
  [{:keys [asset-file]}]
  (let [content (slurp asset-file)
        content-hash (digest/md5 content)
        asset-file-name (fs/file-name asset-file)
        [asset-file-name-no-ext asset-file-ext] (fs/split-ext asset-file-name)
        asset-file-name-hashed (format "%s.%s.%s" asset-file-name-no-ext content-hash asset-file-ext)
        ;asset-file-path-hashed (fs/file (fs/parent asset-file) asset-file-name-hashed)
        target-dir (fs/parent (str/replace asset-file #"^resources/" "resources-hashed/"))
        ;asset-file-path-hashed (fs/file "resources-hashed" "public" "css" asset-file-name-hashed)
        asset-file-path-hashed (fs/file target-dir asset-file-name-hashed)]

    (when-not (fs/exists? (fs/parent asset-file-path-hashed))
      (fs/create-dirs (fs/parent asset-file-path-hashed)))

    ; create hashed asset file
    (spit asset-file-path-hashed content)
    asset-file-path-hashed))
    ; update output asset file name with hash in target file
    ;(fs/update-file target-file #(str/replace % asset-file-name asset-file-name-hashed))))

(defn fetch-js!
  "Fetches a JavaScript file from a URL and saves it to resources/public/js directory with version in the filename.
   
   Parameters:
   - url: URL to fetch the JavaScript file from
   - name: Base name for the file (without extension)
   - version: Version string to include in the filename
   - target-file: Optional file to update references in (similar to hash-css!)
   - old-reference: Optional string to replace in target-file
   
   Returns the path to the saved file."
  [{:keys [url-template filename-template version target-file old-reference]}]
  (let [js-dir ASSET-DIR-JS
        url (str/replace url-template #"\{\{VERSION\}\}" version)
        filename (str/replace filename-template #"\{\{VERSION\}\}" version)
        filepath (str js-dir "/" filename)]

    ; Create js directory if it doesn't exist
    (when-not (fs/exists? js-dir)
      (fs/create-dirs js-dir))

    ; Fetch the file and save it
    (println (format "Fetching %s from %s" filename url))
    (let [response (http/get url)
          content (:body response)]
      (if (= 200 (:status response))
        (do
          ; Save the file
          (fs/create-dirs (fs/parent filepath))
          (spit filepath content)
          (println (format "Saved %s" filepath))
          
          ;; Update references in target file if provided
          ;(when (and target-file old-reference)
          ;  (fs/update-file target-file #(str/replace % old-reference filename)))
          
          filepath)
        (throw (ex-info "Failed to fetch JavaScript file" 
                       {:url url :status (:status response)}))))))



(comment
  (let [url "https://cdn.jsdelivr.net/npm/alpinejs@3.14.8/dist/cdn.min.js"
        assets [{:url-template "https://cdn.jsdelivr.net/npm/alpinejs@{{VERSION}}/dist/cdn.min.js"
                 :filename-template "alpinejs.{{VERSION}}.min.js"
                 :version "3.14.8"}
                {:url-template "https://cdn.jsdelivr.net/npm/@alpinejs/focus@{{VERSION}}/dist/cdn.min.js"
                 :filename-template "alpinejs.focus.{{VERSION}}.min.js"
                 :version "3.14.8"}
                {:url-template "https://unpkg.com/htmx.org@{{VERSION}}/dist/htmx.min.js"
                 :filename-template "htmx.{{VERSION}}.min.js"
                 :version "2.0.4"}]]
    (doseq [item assets]
      (fetch-js! item))))

(comment
  (fs/list-dir "resources/public")
  (fs/components "resources/public")
  (doseq [file (->> (file-seq (fs/file "resources/public"))
                    (remove #(fs/directory? %))
                    ; TODO: move as is!
                    (remove #(contains? #{"json"} (fs/extension %))))]
    (let [file-path (.getPath file)]
          ;new-path (str/replace file-path #"^resources/" "resources-hashed/")]
      ; TODO: generate manifest.edn
      (prn (.getPath (hash-asset-file!-NEW {:asset-file file-path}))))))
      ;(println new-path))))
    ;(println (.getPath file))))

(comment
  (let [asset-files (->> (file-seq (fs/file "resources/public"))
                         (remove #(fs/directory? %))
                         ; TODO: move as is!
                         (remove #(contains? #{"json"} (fs/extension %))))
        manifest-map (reduce
                       (fn [manifest file]
                         (let [source-file-relative (str/replace file #"^resources/public/" "")
                               output-file (hash-asset-file!-NEW {:asset-file (.getPath file)})
                               output-file-relative (str/replace (.getPath output-file) #"^resources-hashed/public/" "")]
                           (assoc manifest source-file-relative output-file-relative)))
                       {}
                       asset-files)]
    (spit (fs/file "resources-hashed" "manifest.edn") (pr-str manifest-map))))
