(ns assets
  (:require [clojure.string :as str]
            [babashka.fs :as fs]
            [clj-commons.digest :as digest]
            [babashka.http-client :as http]))

(def DEFAULT-RESOURCES-DIR "resources")
(def DEFAULT-PUBLIC-DIR "public")
(def DEFAULT-RESOURCES-HASHED-DIR "resources-hashed")
(def DEFAULT-MANIFEST-FILE "manifest.edn")

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
  [{:keys [asset-file target-dir]}]
  (let [content (slurp asset-file)
        content-hash (digest/md5 content)
        asset-file-name (fs/file-name asset-file)
        [asset-file-name-no-ext asset-file-ext] (fs/split-ext asset-file-name)
        asset-file-name-hashed (format "%s.%s.%s" asset-file-name-no-ext content-hash asset-file-ext)
        ;asset-file-path-hashed (fs/file (fs/parent asset-file) asset-file-name-hashed)
        ;asset-file-path-hashed (fs/file "resources-hashed" "public" "css" asset-file-name-hashed)
        asset-file-path-hashed (fs/file target-dir asset-file-name-hashed)]

    (when-not (fs/exists? (fs/parent asset-file-path-hashed))
      (fs/create-dirs (fs/parent asset-file-path-hashed)))

    ; create hashed asset file
    (spit asset-file-path-hashed content)
    asset-file-path-hashed))
    ; update output asset file name with hash in target file
    ;(fs/update-file target-file #(str/replace % asset-file-name asset-file-name-hashed))))

(defn fetch-assets!
  "Fetches an asset file from a URL and saves it to resources/public directory.
   
   Parameters:
   - url: URL to fetch the JavaScript file from
   - filepath: Path to save the file, relative to resources/public

   Returns the path to the saved file."
  [{:keys [url filepath]} target-dir]
  (let [target-filepath (fs/file target-dir filepath)]

    ; Create js directory if it doesn't exist
    (when-not (fs/exists? (fs/parent target-filepath))
      (fs/create-dirs (fs/parent target-filepath)))

    ; Fetch the file and save it
    (println (format "Fetching %s from %s" filepath url))
    (let [response (http/get url)
          content (:body response)]
      (if (= 200 (:status response))
        (do
          ; Save the file
          (spit target-filepath content)
          (println (format "Saved to %s" target-filepath)))
        (throw (ex-info "Failed to fetch JavaScript file"
                       {:url url
                        :status (:status response)
                        :response (:body response)}))))))



(comment
  (let [assets [{:url "https://cdn.jsdelivr.net/npm/alpinejs@3.14.8/dist/cdn.min.js"
                 :filepath "js/alpinejs.min.js"}
                {:url "https://cdn.jsdelivr.net/npm/@alpinejs/focus@3.14.8/dist/cdn.min.js"
                 :filepath "js/alpinejs.focus.min.js"}
                {:url "https://unpkg.com/htmx.org@2.0.4/dist/htmx.min.js"
                 :filepath "js/htmx.min.js"}]
        target-dir (.getPath (fs/file DEFAULT-RESOURCES-DIR DEFAULT-PUBLIC-DIR))]
    (doseq [item assets]
      (fetch-assets! item target-dir))))

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
  (let [asset-files (->> (file-seq (fs/file DEFAULT-RESOURCES-DIR DEFAULT-PUBLIC-DIR))
                         (remove #(fs/directory? %)))
        manifest-map (reduce
                       (fn [manifest file]
                         (let [source-file-relative (->> file
                                                        (fs/components)
                                                        (drop 2)
                                                        (apply fs/file)
                                                        .getPath)
                               target-dir (->> (fs/components file)
                                               (drop 1)
                                               (concat [(fs/path DEFAULT-RESOURCES-HASHED-DIR)])
                                               (apply fs/file)
                                               (fs/parent))
                               _ (prn file)
                               output-file (hash-asset-file!-NEW {:asset-file (.getPath file)
                                                                  :target-dir target-dir})
                               output-file-relative (.getPath (apply fs/file (drop 2 (fs/components output-file))))]
                           (assoc manifest source-file-relative output-file-relative)))
                       {}
                       asset-files)]
    (spit (fs/file DEFAULT-RESOURCES-HASHED-DIR DEFAULT-MANIFEST-FILE) (pr-str manifest-map))))
