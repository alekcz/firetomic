(ns ^:integration datahike-server.integration-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [datahike-server.install :as is]
            [datahike-server.config :as dc]
            [clj-http.client :as client]     
            [datahike-server.test-utils :refer [api-request setup-db] :as utils]))

(defn add-test-data [db]
  (api-request :post "/transact"
               {:tx-data [{:name "Alice" :age 20} {:name "Bob" :age 21}]}
               {:headers {:authorization "token neverusethisaspassword"
                          :db-name db}}))

(defn add-test-schema [db]
  (api-request :post "/transact"
               {:tx-data [{:db/ident :name
                           :db/valueType :db.type/string
                           :db/unique :db.unique/identity
                           :db/cardinality :db.cardinality/one}]}
               {:headers {:authorization "token neverusethisaspassword"
                          :db-name db}}))

(use-fixtures :once setup-db)

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
    (let [_a1 (:databases (api-request :post "/create-database"
                              {:name "testing",
                               :keep-history? true
                               :schema-flexibility :read}
                              {:headers {:authorization "token neverusethisaspassword"}}))
          a2 (try
                (api-request :post "/create-database"
                              {:name "testing"
                               :keep-history? true
                               :schema-flexibility :read}
                              {:headers {:authorization "token neverusethisaspassword"}})
                (catch Exception _ "failed"))
          _a3 (api-request :post "/delete-database"
                {:name "testing",
                  :keep-history? true
                  :schema-flexibility :read}
                {:headers {:authorization "token neverusethisaspassword"}})
          _a4 (:databases (api-request :post "/create-database"
                              {:name "testing",
                               :keep-history? true
                               :schema-flexibility :read}
                              {:headers {:authorization "token neverusethisaspassword"}}))]
      (is (true? (:error a2))))))      

(deftest transact-test
  (let [transact-request (partial api-request :post "/transact")
        body {:tx-data [{:foo 1}]}
        params {:headers {:authorization "token neverusethisaspassword"
                          :db-name "sessions1"}}]
    (testing "Transact values on database without schema"
      (is (= {:tx-data [[1 :foo 1 536870913 true]], :tempids #:db{:current-tx 536870913}, :tx-meta []}
             (transact-request body params))))
    (testing "Transact values on database with schema"
      (is (= {:message "Bad entity attribute :foo at {:db/id 1, :foo 1}, not defined in current schema"}
             (transact-request body (assoc-in params [:headers :db-name] "users1")))))))

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
    (add-test-data "sessions3")
    (is (= "Alice"
           (second (first (api-request :post "/q"
                                       {:query '[:find ?e ?n :in $ ?n :where [?e :name ?n]]
                                        :args ["Alice"]}
                                       {:headers {:authorization "token neverusethisaspassword"
                                                  :db-name "sessions3"}})))))))

(deftest pull-test
  (testing "Fetches data from database using recursive declarative description."
    (add-test-data "sessions4")
    (is (= {:name "Alice"}
           (api-request :post "/pull"
                        {:selector '[:name]
                         :eid 1}
                        {:headers {:authorization "token neverusethisaspassword"
                                   :db-name "sessions4"}})))))
(deftest pull-many-test
  (testing "Same as pull, but accepts sequence of ids and returns sequence of maps."
    (add-test-data "sessions5")
    (is (= [{:name "Alice"} {:name "Bob"}]
           (api-request :post "/pull-many"
                        {:selector '[:name]
                         :eids '(1 2 3 4)}
                        {:headers {:authorization "token neverusethisaspassword"
                                   :db-name "sessions5"}})))))

(deftest datoms-test
  (testing "Index lookup. Returns a sequence of datoms (lazy iterator over actual DB index) which components (e, a, v) match passed arguments."
    (add-test-data "sessions6")
    (is (= 20
           (-> (api-request :post "/datoms"
                            {:index :aevt
                             :components [:age]}
                            {:headers {:authorization "token neverusethisaspassword"
                                       :db-name "sessions6"}})
               first
               (get 2))))))

(deftest seek-datoms-test
  (testing "Similar to datoms, but will return datoms starting from specified components and including rest of the database until the end of the index."
    (add-test-data "sessions7")
    (is (= 20
           (nth (first (api-request :post "/seek-datoms"
                                    {:index :aevt
                                     :components [:age]}
                                    {:headers {:authorization "token neverusethisaspassword"
                                               :db-name "sessions7"}}))
                2)))))

(deftest tempid-test
  (testing "Allocates and returns an unique temporary id."
    (is (number? (:tempid (api-request :get "/tempid"
                                       {}
                                       {:headers {:authorization "token neverusethisaspassword"
                                                  :db-name "sessions8"}}))))))

(deftest entity-test
  (testing "Retrieves an entity by its id from database. Realizes full entity in contrast to entity in local environments."
    (add-test-data "sessions9")
    (is (= {:age 21 :name "Bob"}
           (api-request :post "/entity"
                        {:eid 2}
                        {:headers {:authorization "token neverusethisaspassword"
                                   :db-name "sessions9"}})))))

(deftest schema-test
  (testing "Fetches current schema"
    (add-test-schema "users2")
    (is (= {:name #:db{:ident       :name,
                       :valueType   :db.type/string,
                       :unique  :db.unique/identity
                       :cardinality :db.cardinality/one,
                       :id          1}}
           (api-request :get "/schema"
                        {}
                        {:headers {:authorization "token neverusethisaspassword"
                                   :db-name       "users2"}})))))

(deftest reverse-schema-test
  (testing "Fetches current reverse schema"
    (add-test-schema  "users3")
    (is (= {:db/ident #{:name}
            :db/unique #{:name}
            :db.unique/identity #{:name}
            :db/index #{:name}}
           (api-request :get "/reverse-schema"
                        {}
                        {:headers {:authorization "token neverusethisaspassword"
                                   :db-name       "users3"}})))))

(deftest load-entities-test
  (testing "Loading entities into a new database"
    (api-request :post "/load-entities"
                 {:entities [[100 :foo 200 1000 true]
                             [101 :foo  300 1000 true]
                             [100 :foo 200 1001 false]
                             [100 :foo 201 1001 true]]}
                 {:headers {:authorization "token neverusethisaspassword"
                            :db-name "sessions10"}})
    (is (= [[1 :foo 201 536870914 true]
            [2 :foo 300 536870913 true]]
           (api-request :post "/datoms"
                        {:index :eavt
                         :components []}
                        {:headers {:authorization "token neverusethisaspassword"
                                   :db-name "sessions10"}})))))

(deftest load-config-file-test
  (testing "Loading a file from disk"
    (is (= nil (dc/load-config-file "foo")))
    (is (= nil (dc/load-config-file "test/datahike_server/resources/config.edn.broken")))
    (is (= {:databases [{ :schema-flexibility :read
                          :keep-history? false
                          :name "sessions"}
                        { :name "users"
                          :keep-history? true
                          :schema-flexibility :write}]
            :server { :port  3333
                      :join? false
                      :loglevel :warn
                      :firebase-url "http://localhost:9000"
                      :token :neverusethisaspassword}}
           (dc/load-config-file "test/datahike_server/resources/short.config.edn")))))

(deftest backup-restore-test
  (testing "Backup data base to firebase and restore it."
    (add-test-data "sessions11")
    (let [url (-> 
                (api-request :post "/backup-database"
                              {:backup-name "bakky"}
                              {:headers {:authorization "token neverusethisaspassword"
                                         :db-name "sessions11"}})
                :backup-url)
          _  (api-request :post "/transact"
                          {:tx-data [{:name "Alicia" :age 22} {:name "Bobatea" :age 23}]}
                          {:headers {:authorization "token neverusethisaspassword"
                                      :db-name "sessions11"}})      
          d1 (-> 
                (api-request :post "/datoms"
                              {:index :aevt
                              :components [:age]}
                              {:headers {:authorization "token neverusethisaspassword"
                                        :db-name "sessions11"}})
                count)
          _ (api-request :post "/restore-database"
                    { :name "sessions11"
                      :schema-flexibility :read
                      :keep-history? false
                      :backup-url url}
                    {:headers {:authorization "token neverusethisaspassword"
                                :db-name "sessions11"}})  
          d2 (-> 
                (api-request :post "/datoms"
                              {:index :aevt
                              :components [:age]}
                              {:headers {:authorization "token neverusethisaspassword"
                                        :db-name "sessions11"}})
                count)]  
      (println url)                       
      (is (= 4 d1))                
      (is (= 2 d2)))))
(deftest history-test
  (testing "History with removed entries"
    (add-test-schema "users4")
    (api-request :post "/transact"
                 {:tx-data [{:name "Alice"} {:name "Bob"}]}
                 {:headers {:authorization "token neverusethisaspassword"
                            :db-name "users4"}})
    (api-request :post "/transact"
                 {:tx-data [[:db/retractEntity [:name "Alice"]]]}
                 {:headers {:authorization "token neverusethisaspassword"
                            :db-name       "users4"}})
    (is (= #{[2 "Alice" false] [2 "Alice" true]}
           (set (api-request :post "/q"
                             {:query '[:find ?e ?n ?s :in $ ?n :where [?e :name ?n _ ?s]]
                              :args ["Alice"]}
                             {:headers {:authorization "token neverusethisaspassword"
                                        :db-name       "users4"
                                        :db-history-type "history"}})))))
  (testing "History on non-temporal database"
    (is (= {:message "history is only allowed on temporal indexed databases."}
           (api-request :post "/q"
                        {:query '[:find ?e ?x ?s
                                  :where [?e :foo ?x _ ?s]]}
                        {:headers {:authorization "token neverusethisaspassword"
                                   :db-name       "sessions12"
                                   :db-history-type "history"}})))))

(deftest as-of-test
  (testing "As-of with removed entries"
    (add-test-schema "users5")
    (api-request :post "/transact"
                 {:tx-data [{:name "Alice"} {:name "Bob"}]}
                 {:headers {:authorization "token neverusethisaspassword"
                            :db-name "users5"}})
    (api-request :post "/transact"
                 {:tx-data [[:db/retractEntity [:name "Alice"]]]}
                 {:headers {:authorization "token neverusethisaspassword"
                            :db-name       "users5"}})
    (let [tx-id (->> (api-request :post "/q"
                                  {:query '[:find ?t :where [?t :db/txInstant _ ?t]]}
                                  {:headers {:authorization "token neverusethisaspassword"
                                             :db-name       "users5"}})
                     second
                     first)]
      (is (= #{[2 "Alice"]}
             (set (api-request :post "/q"
                               {:query '[:find ?e ?n :in $ ?n :where [?e :name ?n]]
                                :args ["Alice"]}
                               {:headers {:authorization "token neverusethisaspassword"
                                          :db-name       "users5"
                                          :db-timepoint tx-id
                                          :db-history-type "as-of"}}))))
      (is (= []
             (api-request :post "/q"
                          {:query '[:find ?e ?n :in $ ?n :where [?e :name ?n]]
                           :args ["Alice"]}
                          {:headers {:authorization "token neverusethisaspassword"
                                     :db-name       "users5"}})))))
  (testing "As-of on non-temporal database"
    (is (= {:message "as-of is only allowed on temporal indexed databases."}
           (api-request :post "/q"
                        {:query '[:find ?e ?x ?s
                                  :where [?e :foo ?x _ ?s]]}
                        {:headers {:authorization "token neverusethisaspassword"
                                   :db-name       "sessions13"
                                   :db-history-type "as-of"
                                   :db-timepoint 1}})))))
(deftest since-test
  (testing "Since with removed and new entries"
    (add-test-schema "users6")
    (api-request :post "/transact"
                 {:tx-data [{:name "Alice"} {:name "Bob"}]}
                 {:headers {:authorization "token neverusethisaspassword"
                            :db-name "users6"}})
    (api-request :post "/transact"
                 {:tx-data [{:name "Charlie"}]}
                 {:headers {:authorization "token neverusethisaspassword"
                            :db-name       "users6"}})
    (let [tx-id (->> (api-request :post "/q"
                                  {:query '[:find ?t :where [?t :db/txInstant _ ?t]]}
                                  {:headers {:authorization "token neverusethisaspassword"
                                             :db-name       "users6"}})
                     last
                     first)]
      (is (= #{["Charlie"]}
             (set (api-request :post "/q"
                               {:query '[:find ?n :in $ ?n :where [?e :name ?n]]
                                :args []}
                               {:headers {:authorization "token neverusethisaspassword"
                                          :db-name       "users6"
                                          :db-timepoint tx-id
                                          :db-history-type "since"}}))))
      (is (= #{["Alice"] ["Bob"] ["Charlie"]}
             (set (api-request :post "/q"
                               {:query '[:find ?n :in $ ?n :where [?e :name ?n]]
                                :args []}
                               {:headers {:authorization "token neverusethisaspassword"
                                          :db-name       "users6"}}))))))
  (testing "Since on non-temporal database"
    (is (= {:message "since is only allowed on temporal indexed databases."}
           (api-request :post "/q"
                        {:query '[:find ?e ?x ?s
                                  :where [?e :foo ?x _ ?s]]}
                        {:headers {:authorization "token neverusethisaspassword"
                                   :db-name       "sessions14"
                                   :db-history-type "since"
                                   :db-timepoint 1}})))))
