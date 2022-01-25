(ns datahike-server.config
  (:require [clojure.spec.alpha :as s]
            [taoensso.timbre :as log]
            [mount.core :refer [defstate]]
            [environ.core :refer [env]]
            [datahike.config :refer [int-from-env bool-from-env]]))

(s/fdef load-config-file
  :args (s/cat :config-file string?)
  :ret map?)
(s/fdef load-config
  :args (s/cat :config-file string?)
  :ret map?)
(s/def ::port int?)
(s/def ::join? boolean?)
(s/def ::loglevel #{:trace :debug :info :warn :error :fatal :report})
(s/def ::token keyword?)
(s/def ::dev-mode boolean?)
(s/def ::server-config (s/keys :req-un [::port ::join? ::loglevel]
                               :opt-un [::dev-mode ::token]))

(defn load-config-file [config-file]
  (try
    (-> config-file slurp read-string)
    (catch java.io.FileNotFoundException e (log/info "No config file found at " config-file))
    (catch RuntimeException e (log/info "Could not validate edn in config file " config-file))))

(defn load-config
  "Loads config for Datahike server

   Argument: relative path of config file as string"
  [config-file]
  (let [config-from-file (load-config-file config-file)
        server-config (merge
                       {:port (int-from-env :port (int-from-env :firetomic-port 4000)) 
                        :join? (bool-from-env :firetomic-join? false)
                        :loglevel (keyword (:firetomic-loglevel env :warn))
                        :dev-mode (bool-from-env :firetomic-dev-mode false)}
                       (:server config-from-file))
        token-config (if-let [token (keyword (:firetomic-token env))]
                       (merge
                        {:token (keyword token)}
                        server-config)
                       server-config)
        validated-server-config (if (s/valid? ::server-config token-config)
                                  token-config
                                  (throw (ex-info "Server configuration error:" (s/explain-data ::server-config token-config))))
        firetomic-config  {:store { :backend :firebase 
                                    :db (env :firetomic-firebase-url)
                                    :root (env :firetomic-name)
                                    :env "FIRETOMIC_FIREBASE_AUTH"}
                            :name (env :firetomic-name env)
                            :keep-history? (bool-from-env :firetomic-keep-history false) 
                            :schema-flexibility (or (keyword (env :firetomic-schema-flexibility)) 
                                                    :read)}
                                                         
        firetomic-configs (or (:databases config-from-file) [firetomic-config])]
    {:server validated-server-config
     :databases firetomic-configs}))

(defstate config
  :start (do
           (log/debug "Loading config")
           (load-config "resources/config.edn")))
