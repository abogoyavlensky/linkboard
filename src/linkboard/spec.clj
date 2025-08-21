(ns linkboard.spec
  (:require [lambdaisland.uri :as uri]))

(def Link [:and [:string {:min 1}]
           [:fn {:error/message "must be a valid URL"}
            #(try
               (let [url (if (re-find #"^[a-zA-Z][a-zA-Z0-9+.-]*:" %) % (str "https://" %))
                     parsed (uri/uri url)]
                 (boolean (:host parsed)))
               (catch Exception _ false))]])
