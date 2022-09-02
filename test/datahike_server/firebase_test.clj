(ns ^:integration datahike-server.firebase-test
  (:require [clojure.test :refer [deftest testing is]]
            [datahike-server.database :as db]
            [datahike-server.config :as config]
            [datahike-server.test-utils :refer [api-request]]
            [mount.core :as mount]))

(deftest firebase-test
  (mount/start-with-states {#'datahike-server.config/config
                            {:start #(->  (config/load-config config/config-file-path) 
                                          (assoc-in [:server :dev-mode] true)
                                          (assoc-in [:server :auto-load?] true))
                             :stop (fn [] {})}})
  (let [res (api-request :post "/delete-database" {:name "users" :delete? false})]   
    (is (false? (:deleted res)))
    (is (true? (:disconnected res)))                      
    (is (= {:databases
            [{:store {:backend :firebase :db "http://localhost:9000", :env "FIRETOMIC_FIREBASE_AUTH" :root "sessions"},
              :keep-history? false,
              :schema-flexibility :read,
              :name "sessions",
              :index :datahike.index/hitchhiker-tree
              :attribute-refs? false,
              :cache-size 100000,
              :index-config {:index-b-factor 17, :index-data-node-size 300, :index-log-size 283}}]}
          (api-request :get "/databases"
                        nil
                        {}))))
  (let [res (api-request :post "/create-database" 
              {:name "users" 
               :schema-flexibility "write" 
               :keep-history? false })]
    (is (false? (:created res)))
    (is (true? (:connected res)))                      
    (is (= {:databases
            [{:store {:backend :firebase :db "http://localhost:9000", :env "FIRETOMIC_FIREBASE_AUTH" :root "sessions"},
              :keep-history? false,
              :schema-flexibility :read,
              :name "sessions",
              :index :datahike.index/hitchhiker-tree
              :attribute-refs? false,
              :cache-size 100000,
              :index-config {:index-b-factor 17, :index-data-node-size 300, :index-log-size 283}}
            {:store {:backend :firebase :db "http://localhost:9000", :env "FIRETOMIC_FIREBASE_AUTH" :root "users"},
              :keep-history? true,
              :schema-flexibility :write,
              :name "users",
              :index :datahike.index/hitchhiker-tree
              :attribute-refs? false,
              :cache-size 100000,
              :index-config {:index-b-factor 17, :index-data-node-size 300, :index-log-size 283}}]}
          (api-request :get "/databases"
                        nil
                        {}))))
    (let [schema [{:db/ident :user/name  :db/valueType :db.type/string :db/cardinality :db.cardinality/one}]
          _  (api-request :post "/create-database" 
                {:name "created-db" 
                 :schema-flexibility "read" 
                 :keep-history? false 
                 :initial-tx schema})]
      (is (= 3 (count (api-request :post "/datoms" {:index :eavt} {:headers {:db-name "created-db"}})))))
  (mount/stop))
