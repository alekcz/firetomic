(ns datahike-server.handlers
  (:require [datahike-server.database :refer [conns] :as db]
            [datahike.api :as d]
            [datahike-firebase.core]
            [datahike.core :as c]
            [taoensso.timbre :as log]))

(defn success
  ([data] {:status 200 :body data})
  ([] {:status 200}))

(defn invalid
  ([data] {:status 400 :body data})
  ([] {:status 400}))

(defn list-databases-helper []
  (let [xf (comp
            (map deref)
            (map :config))
        databases' (into [] xf (-> conns vals))
        databases (for [d databases'] (update-in d [:store] dissoc :env))]
    {:databases databases}))

(defn list-databases [_]
  (success (list-databases-helper)))

(defn get-db [{:keys [conn]}]
  (success (select-keys @conn [:meta :config :max-eid :max-tx :hash])))

(defn cleanup-result [result]
  (-> result
      (dissoc :db-after :db-before)
      (update :tx-data #(mapv (comp vec seq) %))
      (update :tx-meta #(mapv (comp vec seq) %))))

(defn transact [{{{:keys [tx-data tx-meta]} :body} :parameters conn :conn}]
  (let [start (System/currentTimeMillis)
        args {:tx-data tx-data
              :tx-meta tx-meta}
        _ (log/info "Transacting with arguments: " args)
        result (d/transact conn args)]
    (-> result
        cleanup-result
        success)))

(defn q [{{:keys [body]} :parameters conn :conn db :db}]
  (let [args {:query (:query body [])
              :args (concat [(or db @conn)] (:args body []))
              :limit (:limit body -1)
              :offset (:offset body 0)}]
    (log/info "Querying with arguments: " (str args))
    (success (into [] (d/q args)))))

(defn pull [{{{:keys [selector eid]} :body} :parameters conn :conn db :db}]
  (success (d/pull (or db @conn) selector eid)))

(defn pull-many [{{{:keys [selector eids]} :body} :parameters conn :conn db :db}]
  (success (vec (d/pull-many (or db @conn) selector eids))))

(defn datoms [{{{:keys [index components]} :body} :parameters conn :conn db :db}]
  (success (mapv (comp vec seq) (apply d/datoms (into [(or db @conn) index] components)))))

(defn seek-datoms [{{{:keys [index components]} :body} :parameters conn :conn db :db}]
  (success (mapv (comp vec seq) (apply d/seek-datoms (into [(or db @conn) index] components)))))

(defn tempid [_]
  (success {:tempid (d/tempid :db.part/db)}))

(defn entity [{{{:keys [eid attr]} :body} :parameters conn :conn db :db}]
  (let [db (or db @conn)]
    (if attr
      (success (get (d/entity db eid) attr))
      (success (->> (d/entity db eid)
                    c/touch
                    (into {}))))))

(defn schema [{:keys [conn]}]        
  (success (d/schema @conn)))

(defn reverse-schema [{:keys [conn]}]
  (success (d/reverse-schema @conn)))

(defn index-range [{{{:keys [attrid start end]} :body} :parameters conn :conn db :db}]
  (let [db (or db @conn)]
    (success (d/index-range db {:attrid attrid
                                :start  start
                                :end    end}))))

(defn load-entities [{{{:keys [entities]} :body} :parameters conn :conn}]
  (-> @(d/load-entities conn entities)
      cleanup-result
      success))
      
(defn create-database [{{:keys [body]} :parameters}]
  (try
    (db/add-database body)
    (success (list-databases-helper)) 
    (catch Exception e
      (invalid (merge {:error true
                       :message (.getMessage e)}
                       (list-databases-helper))))))

(defn backup-database [{{:keys [body]} :parameters conn :conn db :db}]
  (try
    (success {:backup-url (db/backup-database (:backup-name body) conn)})
    (catch Exception e
        (.printStackTrace e)
        (invalid (merge {:error true
                        :message (.getMessage e)}
                        (list-databases-helper))))))
  
(defn restore-database [{{:keys [body]} :parameters _conn :conn _db :db}]
  (success {:backup-url (db/restore-database body)}))
  
(defn delete-database [{{:keys [body]} :parameters}]
  (db/delete-database body)
  (success (list-databases-helper)))       