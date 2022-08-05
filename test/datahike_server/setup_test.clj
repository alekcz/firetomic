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
  (is (= 24 (count (:databases (api-request :get "/databases" nil {})))))
  (mount/stop))
