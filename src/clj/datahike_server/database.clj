(ns datahike-server.database
  (:require [mount.core :refer [defstate stop start] :as mount]
            [taoensso.timbre :as log]
            [datahike-server.config :refer [config]]
            [datahike-firebase.core]
            [datahike.api :as d]
            [fire.core :as fire]
            [fire.auth :as auth])
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

(defn scan-stores [{:keys [databases]}]
  (let [dbs (into [] (set (map #(-> % :store :db) databases)))
        auth (auth/create-token "FIRETOMIC_FIREBASE_AUTH")
        new (for [d (filter some? dbs)]
              (let [names (keys (fire/read d "/" auth {:query {:shallow true}}))]
                (for [n names]
                  (let [store {:store {:backend :firebase :db d :root (name n) :env "FIRETOMIC_FIREBASE_AUTH"}}]
                    (when (d/database-exists? store)
                      (let [c (.-config @(d/connect store))]
                        (assoc-in c [:store :env] "FIRETOMIC_FIREBASE_AUTH")))))))]                        
    (filter some? (flatten new))))

(defn init-connections [{:keys [databases]}]
  (when-not (nil? databases)
    (reduce
     (fn [acc {:keys [name] :as cfg}]
       (when-not (d/database-exists? cfg)
         (log/infof "Creating database...")
         (d/create-database cfg)
         (log/infof "Done"))
       (let [conn (d/connect cfg)]
         (assoc acc (-> @conn :config :name) conn)))
     {}
     databases)))

(defstate conns
  :start (let [existing (scan-stores config)
               final-dbs (into [] (set (concat existing (:databases config))))
               final-config (assoc config :databases (conj final-dbs memdb))]
           (log/debug "Connecting to databases with config: " (str final-config))
           (init-connections final-config))
  :stop (for [conn (vals conns)]
          (d/release conn)))

(defn add-database [{:keys [firebase-url name keep-history? schema-flexibility]}]
  (let [cfg { :store {:backend :firebase 
                      :db firebase-url
                      :root name
                      :env "FIRETOMIC_FIREBASE_AUTH"}
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
    (let [new-conns (assoc conns name (d/connect cfg))]
      (-> 
      (mount/swap {#'datahike-server.database/conns new-conns})
      (mount/start)))))