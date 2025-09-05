(ns hooks.linkboard.test-utils
  (:require [clj-kondo.hooks-api :as api]))

(defn with-chrome
  "Hook for linkboard.test-utils/with-chrome macro.
   Transforms (with-chrome driver ...) into (let [driver {}] ...) 
   so clj-kondo understands the driver binding."
  [{:keys [node]}]
  (let [[_macro-name driver-sym & body] (:children node)]
    (when-not driver-sym
      (api/reg-finding! (assoc (meta node)
                               :message "Missing driver binding symbol"
                               :type :linkboard/missing-driver-binding)))
    (when-not (api/token-node? driver-sym)
      (api/reg-finding! (assoc (meta driver-sym)
                               :message "Driver binding must be a symbol"
                               :type :linkboard/invalid-driver-binding)))
    {:node (api/list-node
            (list*
             (api/token-node 'let)
             (api/vector-node [driver-sym (api/map-node [])])
             body))}))