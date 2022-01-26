;; We'll be back
;; (defproject alekcz/firetomic "0.1.0-SNAPSHOT"
  ;; :description "Firetomic: Datahike Server with Firebase as the backend"
  ;; :url "https://github.com/alekcz/firetomic"
  ;; :license {:name "EPL-2.0 License"
  ;;           :url "https://opensource.org/licenses/EPL-2.0"}

;;   :dependencies     [ [org.clojure/clojure      "1.10.3"]
;;                       [metosin/reitit           "0.5.15"]
;;                       [alekcz/datahike-firebase "0.2.0-20220125.093709-10"]
;;                       [buddy/buddy-auth         "3.0.1"]
;;                       [ring/ring-core           "1.9.4"]
;;                       [ring/ring-jetty-adapter  "1.9.4"]
;;                       [ring-cors/ring-cors      "0.1.13"]
;;                       [com.taoensso/timbre      "5.1.2"]
;;                       [environ/environ          "1.2.0"]
;;                       [mount/mount              "0.1.16"]
;;                       [metosin/spec-tools       "0.10.5"]]
;;   :main datahike-server.core
;;   :auto-clean false
;;   :min-lein-version "2.0.0"
;;   :source-paths ["src/clj"]
;;   :profiles { :uberjar {:aot :all
;;                         :uberjar-name "firetomic-standalone.jar"
;;                         :source-paths ["env/prod/clj"]}

;;               :kaocha {:dependencies [[clj-http/clj-http "3.12.3"]
;;                                       [lambdaisland/kaocha "1.60.972"]
;;                                       [lambdaisland/kaocha-cloverage "1.0.75"]]}}
;;   :aliases
;;     {"kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]})