(ns linkboard.webdriver
  (:require [clj-test-containers.core :as tc]
            [clojure.tools.logging :as log]
            [etaoin.api :as etaoin]
            [integrant.core :as ig])
  (:import [org.testcontainers Testcontainers]))

(def ^:private WEBDRIVER-PORT 4444)

(defmethod ig/init-key ::webdriver
  [_ {:keys [server]}]
  (log/info (str "[DB] Starting webdriver..."))
  (let [server-port (.getLocalPort (first (.getConnectors server)))
        ; Expose port from local machine to container
        _ (Testcontainers/exposeHostPorts (int-array [server-port]))
        container (-> (tc/create {:image-name "selenium/standalone-chromium:131.0"
                                  :exposed-ports [WEBDRIVER-PORT]})
                    (update :container #(.withReuse % true))
                    (tc/start!))]

    {:container container
     :driver (etaoin/chrome-headless {:port (get (:mapped-ports container) WEBDRIVER-PORT)
                                      :host (:host container)
                                      :args ["--no-sandbox"]})}))

(defmethod ig/halt-key! ::webdriver
  [_ {:keys [driver]}]
  (log/info (str "[DB] Closing webdriver..."))
  ; Do not stop the container to be able to reuse it
  (etaoin/quit driver))
