(ns datahike-server.database
  (:require [mount.core :refer [defstate stop start] :as mount]
            [taoensso.timbre :as log]
            [datahike-server.config :refer [config]]
            [datahike-firebase.core]
            [datahike.migrate :refer [export-db import-db]]
            [datahike.api :as d]
            [fire.core :as fire]
            [fire.auth :as auth]
            [fire.storage :as storage]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import [java.util UUID]))

(def memdb {:store {:backend :mem
                    :id "default"}
            :schema-flexibility :read
            :keep-history? false
            :name "default"
            :attribute-refs? false,
            :cache-size 100000,
            :index :datahike.index/hitchhiker-tree,
            :index-config {:index-b-factor 17, :index-data-node-size 300, :index-log-size 283}})

(defn scan-stores [cfg]
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
    (filter some? new)))

(defn prepare-databases [{:keys [server databases] :as configuration}]
  (assoc configuration
    :databases
    (for [cfg databases]
      (let [auth-env (:auth-env server)
            fb-url (:firebase-url server)]
        (if (or (-> cfg :store :backend (= :mem))
                (str/starts-with? (-> cfg :store :db str) "http://")
                (str/starts-with? (-> cfg :store :db str) "https://"))
            cfg 
            (assoc cfg :store {:backend :firebase :db fb-url :root  (-> cfg :name) :env auth-env}))))))

(defn init-connections [{:keys [ databases]}]
  (when-not (nil? databases)
    (let [conns (reduce
                  (fn [coni cfg]
                    (if (d/database-exists? cfg)
                      (assoc coni (-> cfg :name) (d/connect cfg))
                      (do 
                        (log/infof "Creating database...")
                        (d/create-database cfg)
                        (assoc coni (-> cfg :name) (d/connect cfg)))))
                  {}
                  databases)]
      conns)))

(defstate conns
  :start (let [existing (scan-stores config)
               final-dbs (into [] (set (concat existing (:databases config))))
               full-config (assoc config :databases (conj final-dbs memdb))
               final-config (prepare-databases full-config)]
           (log/debug (str "Connecting to databases with config:\n" final-config "\n"))
           (init-connections final-config))
  :stop (for [conn (vals conns)]
          (d/release conn)))

(defn load-datoms [conn backup-url]
  (let [auth (auth/create-token "FIRETOMIC_FIREBASE_AUTH")
        temp (str "temp/" backup-url)]
    (io/make-parents temp)
    (storage/download-to-file backup-url temp auth)
    (log/infof (str "Restoring database from " backup-url "..."))
    (import-db conn temp)))

(defn add-database [{:keys [name keep-history? schema-flexibility backup-url]}]
  (let [cfg { :store {:backend :firebase 
                      :db (-> config :server :firebase-url)
                      :root name
                      :env (-> config :server :auth-env)}
              :name name
              :keep-history? keep-history?
              :schema-flexibility schema-flexibility}]
    (when (contains? conns name)
         (throw (ex-info
                 (str "A database with name '" name "' already exists. Database names on the transactor should be unique.")
                 {:event :connection/initialization
                  :error :database.name/duplicate})))
    (when-not (d/database-exists? cfg)
      (log/infof "Creating database...")
      (d/create-database cfg)  
      (log/infof "Done"))
    (let [new-one (d/connect cfg)
          new-conns (assoc conns name new-one)]
      (when backup-url 
        (load-datoms new-one backup-url))
      (-> 
        (mount/swap {#'datahike-server.database/conns new-conns})
        (mount/start)))))

(defn backup-database [name conn]
  (let [auth (auth/create-token "FIRETOMIC_FIREBASE_AUTH")
        date (new java.util.Date)
        day (.format (java.text.SimpleDateFormat. "YYYY-MM-dd") date)
        full-date (.format (java.text.SimpleDateFormat. "YYYY-MM-dd-HH-mm-ss") date)
        final-name (str "backups/" name "/" day "/" name "-" full-date ".backup.edn")
        temp (str "temp/" final-name)]
    (when conn
      (log/infof (str "Backing up database to " final-name "..."))
      (io/make-parents temp)
      (export-db conn temp)  
      (storage/upload! final-name temp "application/edn" auth)
      (log/infof "Done")
      final-name)))

(defn restore-database [{:keys [name keep-history? schema-flexibility backup-url] :as params}]
  (let [cfg { :store {:backend :firebase 
                      :db (-> config :server :firebase-url)
                      :root name
                      :env (-> config :server :auth-env)}
              :name name
              :keep-history? keep-history?
              :schema-flexibility schema-flexibility}]
    (when (d/database-exists? cfg) 
      (log/infof "Purging database...")
      (d/delete-database cfg))
    (d/create-database cfg)  
    (let [new-conn (d/connect cfg)]
      (load-datoms new-conn backup-url)
      (-> 
        {#'datahike-server.database/conns (assoc conns name new-conn)}
        (mount/swap)
        (mount/start))
      (log/infof "Done"))))
   
(defn delete-database [{:keys [db name]}]
  (let [cfg { :store {:backend :firebase 
                      :db db
                      :root name
                      :env "FIRETOMIC_FIREBASE_AUTH"}
              :name name}]
    (when (d/database-exists? cfg) 
      (log/infof "Deleting database...")
      (d/delete-database cfg)
      (log/infof "Done")
      (-> 
        (mount/swap {#'datahike-server.database/conns (dissoc conns name)})
        (mount/start)))))
    
(defn cleanup-databases []
  (stop #'datahike-server.database/conns)
  (doseq [cfg (:databases config)]
    (log/infof "Purging " cfg " ...")
    (when (d/database-exists? cfg) 
      (d/delete-database cfg))
    (log/infof "Done"))
  (start #'datahike-server.database/conns))

(defn get-db [db-name]
  (if-let [conn (get conns db-name)]
    conn
    (throw (ex-info (format "Database %s does not exist." db-name)
                    {:cause :db-does-not-exist}))))