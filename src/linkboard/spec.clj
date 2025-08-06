(ns linkboard.spec
  (:require [lambdaisland.uri :as uri]))

(def Link [:and [:string {:min 1}]
                [:fn {:error/message "must be a valid URL"}
                 #(try
                    (uri/uri %)
                    (catch Exception _ false))]])
