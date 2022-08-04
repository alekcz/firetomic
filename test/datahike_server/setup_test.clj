(ns ^:integration datahike-server.setup-test
  (:require [clojure.test :refer [deftest testing is]]
            [datahike-server.database :refer [cleanup-databases]]
            [datahike-server.config :as config]
            [datahike-server.test-utils :refer [api-request] :as utils]
            [mount.core :as mount]))

(deftest dev-mode-test
  (mount/start-with-states {#'datahike-server.config/config {:start #(assoc (config/load-config "resources/config.edn") :dev-mode true)
                                                             :stop (fn [] {})}})
  (cleanup-databases)
  (is (= {:databases
           [{:keep-history? true, 
              :index :datahike.index/hitchhiker-tree, 
              :store {:root "users", :backend :firebase, :db utils/fb-url}, 
              :name "users", 
              :attribute-refs? false, 
              :schema-flexibility :write, 
              :index-config {:index-b-factor 17, :index-log-size 283, :index-data-node-size 300}, :cache-size 100000} 
             {:keep-history? false, 
              :index :datahike.index/hitchhiker-tree, 
              :store {:root "sessions", :backend :firebase, :db utils/fb-url}, 
              :initial-tx [{:name "Alice", :age 20} {:name "Bob", :age 21}], 
              :name "sessions", 
              :attribute-refs? false, 
              :schema-flexibility :read, 
              :index-config {:index-b-factor 17, :index-log-size 283, :index-data-node-size 300}, :cache-size 100000} 
             {:store {:id "default", :backend :mem}, 
              :keep-history? false, :schema-flexibility :read, :name "default", :attribute-refs? false, 
              :index :datahike.index/hitchhiker-tree, :cache-size 100000, 
              :index-config {:index-b-factor 17, :index-log-size 283, :index-data-node-size 300}}]}
         (api-request :get "/databases"
                      nil
                      {})))
  (mount/stop))
