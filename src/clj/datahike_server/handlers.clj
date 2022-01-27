(ns datahike-server.handlers
  (:require [datahike-server.database :refer [conns] :as db]
            [datahike.api :as d]
            [datahike.db :as dd]
            [datahike-firebase.core]
            [datahike.core :as c]))

(defn success
  ([data] {:status 200 :body data})
  ([] {:status 200}))

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
  (success {:tx (dd/-max-tx @conn)}))

(defn cleanup-result [result]
  (-> result
      (dissoc :db-after :db-before)
      (update :tx-data #(mapv (comp vec seq) %))
      (update :tx-meta #(mapv (comp vec seq) %))))

(defn transact [{{{:keys [tx-data tx-meta]} :body} :parameters conn :conn}]
  (let [result (d/transact conn {:tx-data tx-data
                                 :tx-meta tx-meta})]

    (-> result
        cleanup-result
        success)))

(defn q [{{:keys [body]} :parameters conn :conn db :db}]
  (success (into []
                 (d/q {:query (:query body [])
                       :args (concat [(or db @conn)] (:args body []))
                       :limit (:limit body -1)
                       :offset (:offset body 0)}))))

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
  (success (dd/-schema @conn)))
  
(defn create-database [{{:keys [body]} :parameters}]
  (try
    (db/add-database body)
    (success (list-databases-helper)) 
    (catch Exception e
      (success (merge (list-databases-helper)
                      {:error true
                       :message (.getMessage e)})))))