(ns datahike-server.server
  (:require [datahike-server.handlers :as h]
            [datahike-server.config :refer [config]]
            [datahike-server.database :refer [conns]]
            [datahike-server.middleware :as middleware]
            [datahike-firebase.core]
            [datahike.api :as d]
            [reitit.ring :as ring]
            [reitit.coercion.spec]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.ring.coercion :as coercion]
            [reitit.dev.pretty :as pretty]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.multipart :as multipart]
            [reitit.ring.middleware.parameters :as parameters]
            [reitit.ring.middleware.dev :as dev]
            [ring.middleware.cors :refer [wrap-cors]]
            [muuntaja.core :as m]
            [clojure.spec.alpha :as s]
            [cemerick.url :as url]
            [taoensso.timbre :as log]
            [mount.core :refer [defstate]]
            [spec-tools.core :as st]
            [org.httpkit.server :refer [run-server server-stop!]]))

(defn long? [x]
  (instance? java.lang.Long x))

(s/def ::entity any?)
(s/def ::tx-data (s/coll-of ::entity))
(s/def ::tx-meta (s/coll-of ::entity))
(s/def ::transactions (s/keys :req-un [::tx-data] :opt-un [::tx-meta]))

(s/def ::query (s/coll-of any?))
(s/def ::args (s/coll-of any?))
(s/def ::limit number?)
(s/def ::offset number?)
(s/def ::query-request (s/keys :req-un [::query]
                               :opt-un [::args ::limit ::offset]))

(s/def ::selector (s/coll-of any?))
(s/def ::eid any?)
(s/def ::pull-request (s/keys :req-un [::selector ::eid]))

(s/def ::eids (s/coll-of ::eid))
(s/def ::pull-many-request (s/keys :req-un [::selector ::eids]))

(s/def ::index #{:eavt :aevt :avet})
(s/def ::components (s/coll-of any?))
(s/def ::datoms-request (s/keys :req-un [::index] :opt-un [::components]))

(s/def ::attr keyword?)
(s/def ::entity-request (s/keys :req-un [::eid] :opt-un [::attr]))

(s/def ::db-name string?)
(s/def ::query-id number?)

(s/def ::attrids [])

(s/def ::conn-header (s/keys :req-un [::db-name]))

(s/def ::db-hash number?)

(s/def ::db-tx int?)
(s/def ::db-header (s/keys :req-un [::db-name]
                           :opt-un [::db-tx]))
(s/def ::params map?)

(s/def ::db (s/and string? #(try (url/url %) (catch Throwable _ false))))
(s/def ::backup-url string?)

(s/def ::name string?)
(s/def ::keep-history? boolean?)
(s/def ::schema-flexibility keyword?)

(s/def ::database (s/keys :req-un [::db ::name ::keep-history? ::schema-flexibility]))

(s/def ::restorable-database (s/keys :req-un [::db ::name ::keep-history? ::schema-flexibility ::backup-url]))
(s/def :entity/vector (s/cat :e long? :a keyword? :v any? :t long? :added boolean?))
(s/def ::entities (s/coll-of :entity/vector))
(s/def ::imported-entities (s/keys :req-un [::entities]))

(def routes
  [["/ping"
    {:get {:no-doc  true
           :handler (constantly {:status 200 :body "pew pew" :headers {"Content-Type" "text/plain"}})}}]

    ["/swagger.json"
    {:get {:no-doc  true
           :swagger {:info {:title       "Datahike API"
                            :description "Transaction and search functions"}}
           :handler (swagger/create-swagger-handler)}}]

   ["/databases"
    {:swagger {:tags ["API"]}
     :get     {:operationId "ListDatabases"
               :summary "List available databases."
               :middleware [middleware/token-auth middleware/auth]
               :handler h/list-databases}}]
               
   ["/create-database"
    {:swagger {:tags ["API"]}
     :post    {:operationId "CreateDatabase"
               :summary "Create a new firetomic database"
               :parameters {:body   (st/spec {:spec ::database
                                              :name "database"})}
               :middleware [middleware/token-auth middleware/auth]
               :handler    h/create-database}}]

   ["/backup-database"
    {:swagger {:tags ["API"]}
     :post    {:operationId "BackupDatabase"
               :summary "Backup a firetomic database"
               :parameters {:body   (st/spec {:spec ::params
                                              :name "backup"})}
               :middleware [middleware/token-auth middleware/auth]
               :handler    h/backup-database}}]

   ["/restore-database"
    {:swagger {:tags ["API"]}
     :post    {:operationId "RestoreDatabase"
               :summary "Create a new firetomic database"
               :parameters {:body   (st/spec {:spec ::restorable-database
                                              :name "restore"})}
               :middleware [middleware/token-auth middleware/auth]
               :handler    h/restore-database}}]

   ["/delete-database"
    {:swagger {:tags ["API"]}
     :post    {:operationId "DeleteDatabase"
               :summary "Delete a existing firetomic database"
               :parameters {:body   (st/spec {:spec ::database
                                              :name "database"})}
               :middleware [middleware/token-auth middleware/auth]
               :handler    h/delete-database}}]                      

   ["/transact"
    {:swagger {:tags ["API"]}
     :post    {:operationId "Transact"
               :summary "Applies transaction to the underlying database value."
               :parameters {:body   (st/spec {:spec ::transactions
                                              :name "transactions"})
                            :header ::conn-header}
               :middleware [middleware/token-auth middleware/auth]
               :handler    h/transact}}]

   ["/db"
    {:swagger {:tags ["API"]}
     :get     {:operationId "DatabaseHash"
               :summary "Get current database as a hash."
               :parameters {:header ::conn-header}
               :middleware [middleware/token-auth middleware/auth]
               :handler    h/get-db-hash}}]

   ["/q"
    {:swagger {:tags ["API"]}
     :post    {:operationId "Query"
               :summary    "Executes a datalog query."
               :parameters {:body   (st/spec {:spec ::query-request
                                              :name "query"})
                            :header ::db-header}
               :middleware [middleware/token-auth middleware/auth]
               :handler    h/q}}]

   ["/pull"
    {:swagger {:tags ["API"]}
     :post    {:operationId "Pull"
               :summary    "Fetches data from database using recursive declarative description."
               :parameters {:body (st/spec {:spec ::pull-request
                                            :name "pull"})
                            :header ::db-header}
               :middleware [middleware/token-auth middleware/auth]
               :handler    h/pull}}]

   ["/pull-many"
    {:swagger {:tags ["API"]}
     :post    {:operationId "PullMany"
               :summary    "Same as [[pull]], but accepts sequence of ids and returns sequence of maps."
               :parameters {:body (st/spec {:spec ::pull-many-request
                                            :name "pull-many"})
                            :header ::db-header}
               :middleware [middleware/token-auth middleware/auth]
               :handler    h/pull-many}}]

   ["/datoms"
    {:swagger {:tags ["API"]}
     :post    {:operationId "Datoms"
               :summary    "Index lookup. Returns a sequence of datoms (lazy iterator over actual DB index) which components (e, a, v) match passed arguments."
               :parameters {:body (st/spec {:spec ::datoms-request
                                            :name "datoms"})
                            :header ::db-header}
               :middleware [middleware/token-auth middleware/auth]
               :handler    h/datoms}}]

   ["/seek-datoms"
    {:swagger {:tags ["API"]}
     :post    {:operationId "SeekDatoms"
               :summary    "Similar to [[datoms]], but will return datoms starting from specified components and including rest of the database until the end of the index."
               :parameters {:body (st/spec {:spec ::datoms-request
                                            :name "seek-datoms"})
                            :header ::db-header}
               :middleware [middleware/token-auth middleware/auth]
               :handler    h/seek-datoms}}]

   ["/tempid"
    {:swagger {:tags ["API"]}
     :get     {:operationId "TempID"
               :summary    "Allocates and returns an unique temporary id."
               :parameters {:header ::conn-header}
               :middleware [middleware/token-auth middleware/auth]
               :handler    h/tempid}}]

   ["/entity"
    {:swagger {:tags ["API"]}
     :post    {:operationId "Entity"
               :summary    "Retrieves an entity by its id from database. Realizes full entity in contrast to entity in local environments."
               :parameters {:body (st/spec {:spec ::entity-request
                                            :name "entity"})
                            :header ::db-header}
               :middleware [middleware/token-auth middleware/auth]
               :handler    h/entity}}]

   ["/schema"
    {:swagger {:tags ["API"]}
     :get     {:operationId "Schema"
               :summary    "Fetches current schema"
               :parameters {:header ::db-header}
               :middleware [middleware/token-auth middleware/auth]
               :handler    h/schema}}]

   ["/reverse-schema"
    {:swagger {:tags ["API"]}
     :get     {:operationId "ReverseSchema"
               :summary    "Fetches current reversed schema"
               :parameters {:header ::db-header}
               :middleware [middleware/token-auth middleware/auth]
               :handler    h/reverse-schema}}]

   ["/load-entities"
    {:swagger {:tags ["API"]}
     :post {:operationId "LoadEntities"
            :summery "Load entities directly"
            :parameters {:header ::db-header
                         :body (st/spec {:spec ::imported-entities
                                         :name "imported-entities"})}
            :middleware [middleware/token-auth middleware/auth]
            :handler h/load-entities}}]])

(def route-opts
  {;; :reitit.middleware/transform dev/print-request-diffs ;; pretty diffs
   ;; :validate spec/validate ;; enable spec validation for route data
   ;; :reitit.spec/wrap spell/closed ;; strict top-level validation
   ; :exception pretty/exception
   :data      {:coercion   reitit.coercion.spec/coercion
               :muuntaja   m/instance
               :middleware [swagger/swagger-feature
                            parameters/parameters-middleware
                            muuntaja/format-negotiate-middleware
                            muuntaja/format-response-middleware
                            ;;  exception/exception-middleware
                            middleware/wrap-fallback-exception
                            middleware/wrap-server-exception
                            middleware/wrap-db-connection
                            middleware/wrap-db-history
                            muuntaja/format-request-middleware
                            coercion/coerce-response-middleware
                            coercion/coerce-request-middleware
                            multipart/multipart-middleware]}})

(def app
  (-> (ring/ring-handler
       (ring/router routes route-opts)
       (ring/routes
        (swagger-ui/create-swagger-ui-handler
         {:path   "/"
          :config {:validatorUrl     nil
                   :operationsSorter "alpha"}})
        (ring/create-default-handler)))
      (wrap-cors :access-control-allow-origin [#"http://localhost" #"http://localhost:8080" #"http://localhost:4000"]
                 :access-control-allow-methods [:get :put :post :delete])))

(defn start-server [config]
  (run-server app (merge {:legacy-return-value? false} (:server config))))

(defstate server
  :start (do
           (log/debug "Starting server")
           (start-server config))
  :stop (server-stop! server))
