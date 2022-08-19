(ns datahike-server.firebase
  (:require [datahike-server.config :as config]
            [taoensso.timbre :as log]
            [fire.core :as fire]
            [fire.auth :as auth]
            [datahike.api :as d]
            [clojure.string :as str])
  (:import [java.util UUID]))

(def memdb {:store {:backend :mem
                    :id "default"}
            :schema-flexibility :read
            :keep-history? false
            :name "default"
            :attribute-refs? false,
            :cache-size 300,
            :index :datahike.index/hitchhiker-tree,
            :index-config {:index-b-factor 17, :index-data-node-size 300, :index-log-size 283}})

(defn scan-stores [cfg]
  (when (-> cfg :server :firebase-url)
    (let [server (:server cfg)
          auth-env (:auth-env server)
          auth  (auth/create-token auth-env)
          fb-url (:firebase-url server)
          new   (let [names (keys (fire/read fb-url "/" auth {:query {:shallow true}}))]
                  (for [n names]
                    (let [store {:store {:backend :firebase :db fb-url :root (name n) :env auth-env}}]
                      (when (d/database-exists? store)
                        (let [c (.-config @(d/connect store))]
                          c)))))]                        
      (filter some? new))))

(defn prepare-databases [{:keys [server databases] :as configuration}]
  (assoc configuration
    :databases
    (vec
      (for [cfg databases]
        (let [auth-env (:auth-env server)
              fb-url (:firebase-url server)]
          (if (or (-> cfg :store :backend (not= :firebase))
                  (str/starts-with? (-> cfg :store :db str) "http://")
                  (str/starts-with? (-> cfg :store :db str) "https://"))
              cfg 
              (assoc cfg :store {:backend :firebase :db fb-url :root  (-> cfg :name) :env auth-env})))))))

(defn connect [config]
  (let [existing (if (-> config :server :auto-load) (vec (scan-stores config)) [])
        final-dbs (concat (:databases config) existing)
        full-config (assoc config :databases final-dbs);(conj final-dbs memdb))
        final-config (prepare-databases full-config)]
    (println (-> config :server :auto-load) existing)
    final-config))

(defn add-database [{:keys [name keep-history? schema-flexibility]} config]
  (let [cfg { :store {:backend :firebase 
                      :db (-> config :server :firebase-url)
                      :root name
                      :env (-> config :server :auth-env)}
              :name name
              :keep-history? keep-history?
              :schema-flexibility (keyword schema-flexibility)
              :cache-size (-> config :server :cache-size)}
        exists? (d/database-exists? cfg)]
    (when-not exists?
      (log/infof "Creating database...")
      (d/create-database cfg)  
      (log/infof "Done"))
    [(not exists?) (d/connect cfg)]))

(defn delete-database [{:keys [name delete?]} config]
  (let [cfg { :store {:backend :firebase 
                      :db (-> config :server :firebase-url)
                      :root name
                      :env (-> config :server :auth-env)}
              :name name}]
    (log/infof (str "Deleting database " name "..."))
    (when delete?
      (when (d/database-exists? cfg) 
        (d/delete-database cfg)))))