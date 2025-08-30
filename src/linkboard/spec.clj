(ns linkboard.spec
  (:import [java.net URI]))

(def Link [:and [:string {:min 1}]
           [:fn {:error/message "must be a valid URL"}
            #(try
               (let [u (URI. %)]
                 (and (.getScheme u) (.getHost u)))
               (catch Exception _ false))]])

(def AccountNumber
  [:re {:error/message "invalid account number format"}
   #"^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$"])
