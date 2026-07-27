(ns com.repldriven.mono.migrator.liquibase
  (:require
    [com.repldriven.mono.jdbc.interface :as jdbc]
    [com.repldriven.mono.error.interface :refer [try-nom]])
  (:import
    (liquibase Contexts LabelExpression Liquibase)
    (liquibase.database Database DatabaseFactory)
    (liquibase.database.jvm JdbcConnection)
    (liquibase.resource ClassLoaderResourceAccessor ResourceAccessor)))

;; Resolution is by classloader, with the changelog named by its full
;; classpath path.
;;
;; It previously resolved the resource to a java.io.File and handed
;; Liquibase the parent directory, which cannot work from inside a jar:
;; `io/resource` returns a `jar:file:...!/...` URL there, and `io/file`
;; on it throws "Not a file". Since an uberjar is exactly how a service
;; is deployed, that made the failure invisible in development and
;; certain in production — `just build` builds uberjars but never runs
;; one, so no test could catch it.
;;
;; The visible difference is what an `include` in a changelog is relative
;; to: the classpath root now, rather than the changelog's own directory.
(defn migrate
  [db-spec resource-path]
  (try-nom
   :migrator/migration-failed
   "Failed to run database migrations"
   (jdbc/on-connection
    [conn db-spec]
    (let [jdbc-connection (JdbcConnection. conn)
          ^Database database (.findCorrectDatabaseImplementation
                              (DatabaseFactory/getInstance)
                              jdbc-connection)
          ^ResourceAccessor accessor (ClassLoaderResourceAccessor.)
          lb (Liquibase. ^String resource-path accessor database)]
      (.update lb (Contexts.) (LabelExpression.))))))
