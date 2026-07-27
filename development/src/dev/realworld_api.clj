(ns dev.realworld-api
  (:require
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.realworld-api.main :as main]))

;; before starting the system:
;; * on Mac OS X, start docker (just start-docker),
;; * start repl (just repl),
;; * connect the repl to your IDE and evaluate file
;; the test config starts its own postgres in a container, so nothing has to
;; be running first, and the :dev profile serves the API on
;; http://localhost:8080/api

(comment
  (def sys (main/start "classpath:realworld-api/application-test.yml" :dev))
  (tap> sys)
  (main/stop sys))

;)
