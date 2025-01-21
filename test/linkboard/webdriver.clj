(ns linkboard.webdriver
  (:require [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [etaoin.api :as etaoin]
            [clj-test-containers.core :as tc])
  (:import [org.testcontainers.containers BrowserWebDriverContainer]
           [org.testcontainers Testcontainers]
           [org.openqa.selenium.chromium ChromiumOptions]))

(defmethod ig/init-key ::webdriver
  [_ {:keys [server]}]
  (log/info (str "[DB] Starting webdriver..."))
  (let [port (.getLocalPort (first (.getConnectors server)))
        ; Expose port from local machine to container
        _ (Testcontainers/exposeHostPorts (int-array [port]))
        container (-> (tc/create {:image-name "selenium/standalone-chromium:131.0"
                                  :exposed-ports [4444]})
                      (tc/start!))]
    {:container container
     :driver (etaoin/chrome-headless {:port (get (:mapped-ports container) 4444)
                                      :host (:host container)
                                      :args ["--no-sandbox"]})}))


(defmethod ig/halt-key! ::webdriver
  [_ {:keys [container driver]}]
  (log/info (str "[DB] Closing webdriver..."))
  (etaoin/quit driver)
  (tc/stop! container))


(comment
  (def DATA (atom (-> (tc/create {:image-name "selenium/standalone-chromium:131.0"
                                  :exposed-ports [4444]})
                      (tc/start!))))

  (let [container @DATA]
    (get (:mapped-ports container) 4444)
    (tc/stop! container)))



(comment
  (Testcontainers/exposeHostPorts (int-array [8000]))
  (def WEB (atom (doto (BrowserWebDriverContainer.)
                   (.withCapabilities (ChromiumOptions.))
                   (.start))))

  (def driver (atom (etaoin/chrome-headless {:port (.getMappedPort @WEB 4444)
                                             :host (.getHost @WEB)
                                             :args ["--no-sandbox"]})))

  (let [d @driver]
    (e/go d "https://linkboard.bogoyavlensky.com")
    (e/refresh d)
    (e/wait-visible d {:tag :h1
                       :fn/has-text "Linkboard"}
                  {:timeout 5})))

