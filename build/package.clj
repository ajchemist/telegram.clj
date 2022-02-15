(ns build.package
  (:require
   [clojure.string :as str]
   [clojure.tools.build.api :as build]
   [user.java.time.script.print-chrono-version :refer [chrono-version-str]]
   ))


(def version (chrono-version-str))
(def class-dir "target/classes")
(def basis (build/create-basis {:project "deps.edn"}))


(def github-scm-url
  (let [github-server-url (System/getenv "GITHUB_SERVER_URL")
        github-repository (System/getenv "GITHUB_REPOSITORY")]
    (when-not (or (str/blank? github-server-url) (str/blank? github-repository))
      (str github-server-url "/" github-repository))))


(defn clean
  [_]
  (build/delete {:path "target"}))


(defn jar
  [{:keys [lib scm-url]}]
  (build/write-pom {:class-dir class-dir
                    :lib       lib
                    :version   version
                    :basis     basis
                    :scm       {:url (or scm-url github-scm-url "")}
                    :src-dirs  ["src/core"]})
  (build/copy-file {:src    (build/pom-path {:class-dir class-dir :lib lib})
                    :target "pom.xml"})
  (build/copy-dir {:src-dirs   ["src/core"]
                   :target-dir class-dir})
  (build/jar {:class-dir class-dir
              :jar-file  "target/package.jar"}))
