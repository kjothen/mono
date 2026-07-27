(ns ^:eftest/synchronized com.repldriven.mono.migrator.jar-changelog-test
  "A changelog has to resolve when it is inside a jar, because that is how a
  service is deployed.

  This is not hypothetical. Resolving the resource to a `java.io.File` and
  handing Liquibase its parent directory works from a directory classpath and
  cannot work from a jar, so the failure was invisible in development and
  certain in production. `just build` builds uberjars but never runs one, so
  no existing test could see it.

  The jar here is built at runtime and pushed onto the classloader, which is
  the cheapest way to get a genuine `jar:file:...!/...` resource without a
  packaging step."
  (:require
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.migrator.interface :as SUT]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.jdbc.interface :as jdbc]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer [with-test-system]]

    [clojure.java.io :as io]
    [clojure.test :refer [deftest is testing]])
  (:import
    (java.io File)
    (java.net URL URLClassLoader)
    (java.util.jar JarEntry JarOutputStream)))

(def ^:private changelog-entry "jarred/changelog.sql")

(def ^:private changelog
  (str "--liquibase formatted sql\n\n" "--changeset jarred:1\n"
       "create table jarred(id serial, name varchar(255));\n"
       "insert into jarred(name) values('from-a-jar');\n"))

(defn- changelog-jar
  "A deleted-on-exit jar holding the changelog."
  []
  (let [jar (File/createTempFile "migrator-changelog" ".jar")]
    (.deleteOnExit jar)
    (with-open [out (JarOutputStream. (io/output-stream jar))]
      (.putNextEntry out (JarEntry. changelog-entry))
      (.write out (.getBytes changelog "UTF-8"))
      (.closeEntry out))
    jar))

(defn- with-jar-on-classpath
  "Run `f` with `jar` on the thread's context classloader, restoring it after.

  The context classloader rather than `RT/baseLoader`, because the external
  test runner's base loader is the AppClassLoader and cannot be extended."
  [jar f]
  (let [thread (Thread/currentThread)
        original (.getContextClassLoader thread)
        loader (URLClassLoader. (into-array URL [(.toURL (.toURI jar))])
                                original)]
    (try (.setContextClassLoader thread loader)
         (f loader)
         (finally (.setContextClassLoader thread original)))))

(deftest jar-changelog-test
  (with-jar-on-classpath
   (changelog-jar)
   (fn [loader]
     (testing "the changelog really is inside a jar, not on disk"
       ;; Guards the test itself: were this a plain file, the case below
       ;; would pass without exercising anything.
       (let [url (io/resource changelog-entry loader)]
         (is (some? url))
         (is (= "jar" (.getProtocol url)))
         (is (error/anomaly?
              (error/try-nom :test/io-file "" (io/file (.toURI url))))
             "io/file on a jar resource throws — this is what used to break")))
     (testing "a changelog inside a jar applies"
       (with-test-system
        [sys "classpath:migrator/application-test.yml"]
        (let [datasource (system/instance sys [:jdbc :datasource])
              db-spec (jdbc/get-datasource datasource)]
          (is (not (error/anomaly? (SUT/migrate db-spec changelog-entry))))
          (is (= [{:name "from-a-jar"}]
                 (jdbc/execute! datasource ["select name from jarred"])))))))))
