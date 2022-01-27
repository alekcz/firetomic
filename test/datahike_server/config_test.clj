(ns datahike-server.config-test
  (:require [clojure.test :refer :all]
            [datahike-server.config :refer :all]))

(deftest bad-config-test
  (testing "Loading bad config file"
    (with-redefs [load-config-file (constantly {:server {:join? :baz}})]
      (is (thrown? clojure.lang.ExceptionInfo
                   (load-config "redef1")))
      (is (thrown? clojure.lang.ExceptionInfo
                   (load-config :redef2))))
    (with-redefs [load-config-file (constantly {:server {:join? true :port 1 :loglevel :meh}})]
      (is (thrown? clojure.lang.ExceptionInfo
                   (load-config "redef3"))))
    (with-redefs [load-config-file (constantly {:server {:join? true :port 2 :loglevel :fatal :token nil}})]
      (is (thrown? clojure.lang.ExceptionInfo
                   (load-config "redef4"))))
    (with-redefs [load-config-file (constantly {:server {:join? true :port 3 :loglevel :fatal :token nil :dev-mode false}})]
      (is (thrown? clojure.lang.ExceptionInfo
                   (load-config "redef5"))))))

(deftest config-test
  (testing "Loading good config file"
    (with-redefs [load-config-file (constantly {:databases [{:backend :file
                                                             :path "/foo/bar"}]
                                                :server {:port 1
                                                         :join? true
                                                         :loglevel :fatal
                                                         :dev-mode false
                                                         :token :foo}})]
      (is (= {:server {:port 1
                       :join? true
                       :loglevel :fatal
                       :token :foo
                       :dev-mode false}
              :databases [{:backend :file
                           :path "/foo/bar"}]}
             (load-config "foobar"))))
    (with-redefs [load-config-file (constantly {:databases [{:backend :pg
                                                             :username "tester"
                                                             :password "test"
                                                             :host "testhost"
                                                             :port 2
                                                             :path "/testdb"}]
                                                :server {:port 2
                                                         :join? true
                                                         :loglevel :fatal
                                                         :token :foo}})]
      (is (= {:server {:port 2
                       :join? true
                       :loglevel :fatal
                       :dev-mode false
                       :token :foo}
              :databases [{:backend :pg,
                           :username "tester",
                           :password "test",
                           :host "testhost",
                           :port 2,
                           :path "/testdb"}]}
             (load-config "foobar"))))))