(ns linkboard.spec
  (:require [lambdaisland.uri :as uri]))

(def Link [:and [:string {:min 1}]
           [:fn {:error/message "must be a valid URL"}
            #(try
               (let [parsed (uri/uri %)]
                 (boolean (:host parsed)))
               (catch Exception _ false))]])
