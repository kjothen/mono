(ns com.repldriven.mono.server.interface
  (:require
    com.repldriven.mono.server.system
    [com.repldriven.mono.server.core :as core]
    [com.repldriven.mono.server.cors :as cors]
    [com.repldriven.mono.server.interceptors :as interceptors]))

(def require-idempotency-key
  "Interceptor that validates the `Idempotency-Key` header is present
  and syntactically well-formed (16-255 URL-safe ASCII chars)."
  interceptors/require-idempotency-key)

(def standard-router-data core/standard-router-data)
(def standard-executor core/standard-executor)
(def default-exception-handlers core/default-exception-handlers)

(defn router-data
  ([] (core/router-data))
  ([exception-handlers] (core/router-data exception-handlers)))

(defn standard-default-handler [] (core/standard-default-handler))

(defn standard-openapi-handler [] (core/standard-openapi-handler))

(defn standard-openapi-ui-handler [] (core/standard-openapi-ui-handler))

(defn http-local-url
  "Get the local HTTP URL from a Jetty Server instance."
  [server]
  (core/http-local-url server))

(defn health-routes
  "Spring Boot Actuator-style health endpoints:
  - `/actuator/health/liveness` always returns `{\"status\":\"UP\"}` (200).
  - `/actuator/health/readiness` returns UP (200) or DOWN (503)
    based on `(:ready-fn ctx)`.
  - `/actuator/health` aggregates the two groups.
  `ready-fn` comes from the API ctx — the jetty-adapter system
  component threads it through, defaulting to `(constantly true)`
  for services without a startup dependency. Adapter services
  pass an atom-backed readiness component (jetty-adapter coerces
  any IDeref to a thunk) so readiness stays DOWN until webhook
  registration succeeds."
  [ctx]
  (let [ready-fn (or (:ready-fn ctx) (constantly true))
        json {"content-type" "application/json"}
        liveness (fn [_] {:status 200 :headers json :body {:status "UP"}})
        readiness (fn [_]
                    (if (ready-fn)
                      {:status 200 :headers json :body {:status "UP"}}
                      {:status 503 :headers json :body {:status "DOWN"}}))
        aggregate (fn [_]
                    (let [ready? (boolean (ready-fn))]
                      {:status (if ready? 200 503)
                       :headers json
                       :body {:status (if ready? "UP" "DOWN")
                              :components
                              {:liveness {:status "UP"}
                               :readiness {:status
                                           (if ready? "UP" "DOWN")}}}}))]
    [["/actuator/health" {:get {:no-doc true :handler aggregate}}]
     ["/actuator/health/liveness" {:get {:no-doc true :handler liveness}}]
     ["/actuator/health/readiness" {:get {:no-doc true :handler readiness}}]]))

(defn wrap-cors
  "Wrap `handler` so browsers may call it from `:origins`.

  Middleware rather than an interceptor: a preflight arrives as OPTIONS on a
  path whose route declares no OPTIONS handler, which the router answers
  with a 404 before any interceptor runs.

  Returns `handler` unchanged when no origins are configured, so the default
  is to permit nothing.

  Args:
  - handler: the Ring handler to wrap.
  - opts: `{:origins [\"http://localhost:3000\"] :methods [...]
    :request-headers [...] :max-age 3600 :credentials? false}`. Only
    `:origins` is required."
  [handler opts]
  (cors/wrap-cors handler opts))
