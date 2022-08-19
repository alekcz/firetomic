(ns ^:integration datahike-server.setup-test
  (:require [clojure.test :refer [deftest testing is]]
            [datahike-server.database :as db]
            [datahike-server.config :as config]
            [datahike-server.test-utils :refer [api-request]]
            [mount.core :as mount]))

(deftest dev-mode-test
  (mount/start-with-states {#'datahike-server.config/config
                            {:start #(assoc-in (config/load-config config/config-file-path) [:server :dev-mode] true)
                             :stop (fn [] {})}})
  (db/cleanup-databases)
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
                      {})))
  (mount/stop))
