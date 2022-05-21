(ns ^:integration datahike-server.setup-test
  (:require [clojure.test :refer [deftest testing is]]
            [datahike-server.database :refer [cleanup-databases]]
            [datahike-server.config :as config]
            [datahike-server.test-utils :refer [api-request]]
            [mount.core :as mount]))

(deftest dev-mode-test
  (mount/start-with-states {#'datahike-server.config/config {:start #(assoc (config/load-config "resources/config.edn") :dev-mode true)
                                                             :stop (fn [] {})}})
  (cleanup-databases)
  (is (= {:databases
          [{:store {:backend :firebase 
                      :db "http://localhost:9000/prod" 
                      :root "sessions"}:keep-history? false,
            :schema-flexibility :read,
            :name "sessions",
            :index :datahike.index/hitchhiker-tree
            :attribute-refs? false,
            :cache-size 100000,
            :index-config {:index-b-factor 17, :index-data-node-size 300, :index-log-size 283}}
           {:store {:backend :firebase 
                      :db "http://localhost:9000/prod" 
                      :root "users"}
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
