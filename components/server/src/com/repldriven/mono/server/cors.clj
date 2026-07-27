(ns com.repldriven.mono.server.cors
  (:require
    [clojure.string :as str]))

(def default-methods ["GET" "POST" "PUT" "PATCH" "DELETE" "OPTIONS"])

(def default-headers ["Accept" "Authorization" "Content-Type"])

(def default-max-age 3600)

(defn- ->set
  "One origin or many, blanks dropped.

  A bare string would otherwise become a set of its characters. Blanks are
  reachable from config — an env var set but empty resolves to \"\" rather
  than nil — and a whitelist containing \"\" matches nothing while still
  looking configured."
  [origins]
  (into #{}
        (remove str/blank?)
        (if (string? origins) [origins] origins)))

(defn- allowed
  "The origin to echo back, or nil.

  Echoed rather than listed: `Access-Control-Allow-Origin` takes one origin
  or `*`, so a whitelist has to answer per request."
  [origin origins]
  (when (and origin (contains? (->set origins) origin)) origin))

(defn- headers
  [origin opts]
  (let [{:keys [methods request-headers max-age credentials?]} opts]
    (cond-> {"Access-Control-Allow-Origin" origin
             "Access-Control-Allow-Methods" (str/join ", "
                                                      (or methods
                                                          default-methods))
             "Access-Control-Allow-Headers" (str/join ", "
                                                      (or request-headers
                                                          default-headers))
             "Access-Control-Max-Age" (str (or max-age default-max-age))
             ;; Without this a whitelist behaves like `*` to a shared
             ;; cache, which could serve one origin's response to another.
             "Vary" "Origin"}
            credentials?
            (assoc "Access-Control-Allow-Credentials" "true"))))

;; Middleware rather than an interceptor, because a preflight arrives as
;; OPTIONS on a path whose route declares no OPTIONS handler. Inside the
;; router that is a 404 before any interceptor of ours runs, so this has to
;; sit outside it.
(defn wrap-cors
  [handler opts]
  (let [{:keys [origins]} opts]
    (if (empty? (->set origins))
      handler
      (fn [request]
        (let [origin (allowed (get-in request [:headers "origin"]) origins)]
          (cond
           (and origin (= :options (:request-method request)))
           {:status 204 :headers (headers origin opts) :body nil}

           origin
           (let [response (handler request)]
             (update response :headers merge (headers origin opts)))

           :else
           (handler request)))))))
