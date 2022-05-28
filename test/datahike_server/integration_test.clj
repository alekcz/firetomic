(ns ^:integration datahike-server.integration-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [datahike-server.install :as is]
            [datahike-server.database :as db]       
            [datahike-server.config :as dc]
            [clj-http.client :as client]     
            [datahike-server.test-utils :refer [api-request setup-db] :as tu]))

(defn add-test-data []
  (api-request :post "/transact"
               {:tx-data [{:name "Alice" :age 20} {:name "Bob" :age 21}]}
               {:headers {:authorization "token neverusethisaspassword"
                          :db-name "sessions"}}))

(defn add-test-schema []
  (api-request :post "/transact"
               {:tx-data [{:db/ident :name
                           :db/valueType :db.type/string
                           :db/cardinality :db.cardinality/one}]}
               {:headers {:authorization "token neverusethisaspassword"
                          :db-name "users"}}))

(use-fixtures :each setup-db)

(deftest install-test
  (testing "that deps will be forced to download in docker"
    (is (= "dependencies downloaded" (with-out-str (is/install nil))))))

(deftest swagger-test
  (testing "Swagger Json"
    (is (= {:title "Datahike API"
            :description "Transaction and search functions"}
           (:info (api-request :get
                               "/swagger.json"
                               nil
                               {:headers {:authorization "token neverusethisaspassword"}}))))))

(deftest ping-test
  (testing "Ping"
    (is (= "pew pew" (:body (client/request {:url "http://localhost:3333/ping" :method :get}))))))

(deftest databases-test
  (testing "Get Databases"
    (let [a1 (:databases (api-request :post "/create-database"
                              {:db tu/fb-root
                               :name "testing",
                               :keep-history? true
                               :schema-flexibility :read}
                              {:headers {:authorization "token neverusethisaspassword"}}))
          a2 (try
                (api-request :post "/create-database"
                              {:db tu/fb-root
                              :name "testing"
                              :keep-history? true
                              :schema-flexibility :read}
                              {:headers {:authorization "token neverusethisaspassword"}})
                (catch Exception _ "failed"))]
      (is (= "failed" a2))
      (is (= {:databases
              [{:store {:root "sessions", 
                        :backend :firebase, 
                        :db tu/test-root}, 
                :name "sessions", 
                :keep-history? false, 
                :index :datahike.index/hitchhiker-tree, 
                :attribute-refs? false, 
                :schema-flexibility :read, 
                :index-config {:index-b-factor 17, :index-log-size 283, :index-data-node-size 300}, :cache-size 100000} 
              {:store {:root "users", 
                        :backend :firebase, 
                        :db tu/test-root}, 
                :keep-history? true, 
                :index :datahike.index/hitchhiker-tree, 
                :name "users", 
                :attribute-refs? false, 
                :schema-flexibility :write, 
                :index-config {:index-b-factor 17, :index-log-size 283, :index-data-node-size 300}, :cache-size 100000} 
              { :store {:id "default", :backend :mem}, 
                :keep-history? false, 
                :schema-flexibility :read, 
                :name "default", 
                :attribute-refs? false, 
                :index :datahike.index/hitchhiker-tree, 
                :cache-size 100000, 
                :index-config {:index-b-factor 17, :index-log-size 283, :index-data-node-size 300}}
              { :store {:backend :firebase,
                         :db "https://alekcz-dev.firebaseio.com/firetomic-test",
                         :root "testing"}
                :attribute-refs? false,
                :cache-size 100000,
                :index :datahike.index/hitchhiker-tree,
                :index-config {:index-b-factor 17, :index-data-node-size 300, :index-log-size 283},
                :keep-history? true,
                :name "testing",
                :schema-flexibility :read}]}
            (api-request :get "/databases"
                          nil
                          {:headers {:authorization "token neverusethisaspassword"}}))))))

(deftest transact-test
  (testing "Transact values"
    (is (= {:tx-data [[1 :foo 1 536870913 true]], :tempids #:db{:current-tx 536870913}, :tx-meta []}
           (api-request :post "/transact"
                        {:tx-data [{:foo 1}]}
                        {:headers {:authorization "token neverusethisaspassword"
                                   :db-name "sessions"}})))))

(deftest db-test
  (testing "Get current database as a hash"
    (is (= 0 (:hash (api-request :get "/db"
                                 nil
                                 {:headers {:authorization "token neverusethisaspassword"
                                            :db-name "sessions"}}))))
    (is (= 0 (:hash (api-request :get "/db"
                                 nil
                                 {:headers {:authorization "token neverusethisaspassword"
                                            :db-name "users"}}))))))

(deftest q-test
  (testing "Executes a datalog query"
    (add-test-data)
    (is (= "Alice"
           (second (first (api-request :post "/q"
                                       {:query '[:find ?e ?n :in $ ?n :where [?e :name ?n]]
                                        :args ["Alice"]}
                                       {:headers {:authorization "token neverusethisaspassword"
                                                  :db-name "sessions"}})))))))

(deftest pull-test
  (testing "Fetches data from database using recursive declarative description."
    (add-test-data)
    (is (= {:name "Alice"}
           (api-request :post "/pull"
                        {:selector '[:name]
                         :eid 1}
                        {:headers {:authorization "token neverusethisaspassword"
                                   :db-name "sessions"}})))))
(deftest pull-many-test
  (testing "Same as pull, but accepts sequence of ids and returns sequence of maps."
    (add-test-data)
    (is (= [{:name "Alice"} {:name "Bob"}]
           (api-request :post "/pull-many"
                        {:selector '[:name]
                         :eids '(1 2 3 4)}
                        {:headers {:authorization "token neverusethisaspassword"
                                   :db-name "sessions"}})))))

(deftest datoms-test
  (testing "Index lookup. Returns a sequence of datoms (lazy iterator over actual DB index) which components (e, a, v) match passed arguments."
    (add-test-data)
    (is (= 20
           (-> (api-request :post "/datoms"
                            {:index :aevt
                             :components [:age]}
                            {:headers {:authorization "token neverusethisaspassword"
                                       :db-name "sessions"}})
               first
               (get 2))))))

(deftest seek-datoms-test
  (testing "Similar to datoms, but will return datoms starting from specified components and including rest of the database until the end of the index."
    (add-test-data)
    (is (= 20
           (nth (first (api-request :post "/seek-datoms"
                                    {:index :aevt
                                     :components [:age]}
                                    {:headers {:authorization "token neverusethisaspassword"
                                               :db-name "sessions"}}))
                2)))))

(deftest tempid-test
  (testing "Allocates and returns an unique temporary id."
    (is (number? (:tempid (api-request :get "/tempid"
                                       {}
                                       {:headers {:authorization "token neverusethisaspassword"
                                                  :db-name "sessions"}}))))))

(deftest entity-test
  (testing "Retrieves an entity by its id from database. Realizes full entity in contrast to entity in local environments."
    (add-test-data)
    (is (= {:age 21 :name "Bob"}
           (api-request :post "/entity"
                        {:eid 2}
                        {:headers {:authorization "token neverusethisaspassword"
                                   :db-name "sessions"}})))))

(deftest schema-test
  (testing "Fetches current schema"
    (add-test-schema)
    (is (= {:name #:db{:ident       :name,
                       :valueType   :db.type/string,
                       :cardinality :db.cardinality/one,
                       :id          1}}
           (api-request :get "/schema"
                        {}
                        {:headers {:authorization "token neverusethisaspassword"
                                   :db-name       "users"}})))))

(deftest reverse-schema-test
  (testing "Fetches current reverse schema"
    (add-test-schema)
    (is (= {:db/ident #{:name}}
           (api-request :get "/reverse-schema"
                        {}
                        {:headers {:authorization "token neverusethisaspassword"
                                   :db-name       "users"}})))))

(deftest load-entities-test
  (testing "Loading entities into a new database"
    (api-request :post "/load-entities"
                 {:entities [[100 :foo 200 1000 true]
                             [101 :foo  300 1000 true]
                             [100 :foo 200 1001 false]
                             [100 :foo 201 1001 true]]}
                 {:headers {:authorization "token neverusethisaspassword"
                            :db-name "sessions"}})
    (is (= [[1 :foo 201 536870914 true]
            [2 :foo 300 536870913 true]]
           (api-request :post "/datoms"
                        {:index :eavt
                         :components []}
                        {:headers {:authorization "token neverusethisaspassword"
                                   :db-name "sessions"}})))))

(deftest load-config-file-test
  (testing "Loading a file from disk"
    (is (= nil (dc/load-config-file "foo")))
    (is (= nil (dc/load-config-file "test/datahike_server/resources/config.edn.broken")))
    (is (= {:databases [{:store { :backend :firebase 
                                  :db "http://localhost:9000/prod" 
                                  :root "sessions"}
                          :schema-flexibility :read
                          :keep-history? false
                          :name "sessions"}
                        {:store { :backend :firebase 
                                  :db "http://localhost:9000/prod" 
                                  :root "users"}
                          :name "users"
                          :keep-history? true
                          :schema-flexibility :write}]
            :server {:port  3333
                     :join? false
                     :loglevel :debug
                     :token :neverusethisaspassword}}
           (dc/load-config-file "test/datahike_server/resources/config.edn")))))

(deftest backup-restore-test
  (testing "Backup data base to firebase and restore it."
    (add-test-data)
    (let [url (-> 
                (api-request :post "/backup-database"
                              {:backup-name "bakky"}
                              {:headers {:authorization "token neverusethisaspassword"
                                         :db-name "sessions"}})
                :backup-url)
          _  (api-request :post "/transact"
                          {:tx-data [{:name "Alicia" :age 22} {:name "Bobatea" :age 23}]}
                          {:headers {:authorization "token neverusethisaspassword"
                                      :db-name "sessions"}})      
          d1 (-> 
                (api-request :post "/datoms"
                              {:index :aevt
                              :components [:age]}
                              {:headers {:authorization "token neverusethisaspassword"
                                        :db-name "sessions"}})
                count)
          _ (api-request :post "/restore-database"
                    { :db tu/test-root
                      :name "sessions"
                      :schema-flexibility :read
                      :keep-history? false
                      :backup-url url}
                    {:headers {:authorization "token neverusethisaspassword"
                                :db-name "sessions"}})  
          d2 (-> 
                (api-request :post "/datoms"
                              {:index :aevt
                              :components [:age]}
                              {:headers {:authorization "token neverusethisaspassword"
                                        :db-name "sessions"}})
                count)]  
      (println url)                       
      (is (= 4 d1))                
      (is (= 2 d2)))))
