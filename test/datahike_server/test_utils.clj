(ns datahike-server.test-utils
  (:require [clojure.edn :as edn]
            [datahike-server.database :refer [cleanup-databases]]
            [datahike-server.core :refer [start-all stop-all]]
            [clj-http.client :as client]))

(def test-root "http://localhost:9000/prod")
(def fb-root "https://alekcz-dev.firebaseio.com/firetomic-test")

(defn parse-body [{:keys [body]}]
  (if-not (empty? body)
    (edn/read-string body)
    ""))

(defn api-request
  ([method url]
   (api-request method url nil nil))
  ([method url data]
   (api-request method url data nil))
  ([method url data opts]
   (-> (client/request (merge {:url (str "http://localhost:3333" url)
                               :method method
                               :content-type "application/edn"
                               :accept "application/edn"}
                              (when (or (= method :post) data)
                                {:body (str data)})
                              opts))
       parse-body)))

(defn setup-db [f]
  (start-all)
  (cleanup-databases)
  (api-request :post "/delete-database"
                  {:db fb-root
                   :name "testing"
                   :keep-history? true
                   :schema-flexibility :read}
                  {:headers {:authorization "token neverusethisaspassword"}})
  (f)
  (stop-all))

(defn no-env [xs]
  (for [x xs] (update-in x [:store] dissoc :env)))