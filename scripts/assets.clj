(ns assets
  (:require [clojure.string :as str]
            [babashka.fs :as fs]
            [clj-commons.digest :as digest]))


(defn hash-css!
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
