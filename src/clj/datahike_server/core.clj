(ns datahike-server.core
  (:require [mount.core :as mount]
            [taoensso.timbre :as log]
            [datahike-server.config :refer [config]]
            [datahike-server.database]
            [datahike-server.server]))

(defn start-all []
  (mount/start))

(defn stop-all []
  (mount/stop))

(defn -main [& _]
  (mount/start)
  (log/set-level! (get-in config [:server :loglevel]))
  (log/debugf "Datahike Server Running!"))