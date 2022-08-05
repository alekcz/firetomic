(ns datahike-server.test-utils
  (:require [clojure.edn :as edn]
            [datahike-server.database :refer [cleanup-databases]]
            [datahike-server.core :refer [start-all stop-all -main]]
            [clj-http.client :as client]))

(def fb-url "http://localhost:9000")

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
                               :throw-exceptions? false
                               :content-type "application/edn"
                               :accept "application/edn"}
                              (when (or (= method :post) data)
                                {:body (str data)})
                              opts))
       parse-body)))

(defn setup-db [f]
  (-main)
  (stop-all)
  (start-all)
  (cleanup-databases)
  (f)
  (cleanup-databases)
  (stop-all))

(defn no-env [xs]
  (for [x xs] (update-in x [:store] dissoc :env)))