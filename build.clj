(ns build
  (:require
    [borkdude.gh-release-artifact :as gh]
    [clojure.tools.build.api :as b]
    [clojure.java.shell :refer [sh]]))

(def lib 'alekcz/firetomic)
(def version (slurp "resources/VERSION"))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-path (format "target/firetomic-%s.jar" version))
(def uber-file "firetomic-standalone.jar")
(def uber-path (format "target/%s" uber-file))
(def image (format "docker.io/alekcz/firetomic:%s" version))

(defn get-version
  [_]
  (println version))

(defn clean
  [_]
  (b/delete {:path "target"}))

(defn compile
  [_]
  (b/javac {:src-dirs ["java"]
            :class-dir class-dir
            :basis basis
            :javac-opts ["-source" "8" "-target" "8"]}))

(defn jar
  [_]
  (compile nil)
  (b/write-pom {:class-dir class-dir
                :src-pom "./template/pom.xml"
                :lib lib
                :version version
                :basis basis
                :src-dirs ["src"]})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-path}))

(defn uber
  [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-path
           :basis basis
           :main 'datahike-server.core}))
