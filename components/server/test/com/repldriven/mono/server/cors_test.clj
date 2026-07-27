(ns com.repldriven.mono.server.cors-test
  (:require
    [com.repldriven.mono.server.interface :as SUT]

    [clojure.test :refer [deftest is testing]]))

(def ^:private origins ["http://localhost:3000"])

(defn- handler
  [_request]
  {:status 200 :headers {"Content-Type" "application/json"} :body "{}"})

(defn- call
  [opts request]
  ((SUT/wrap-cors handler opts) request))

(deftest wrap-cors-test
  (testing "an allowed origin is echoed back, not listed"
    ;; Access-Control-Allow-Origin takes one origin or *, so a whitelist
    ;; has to answer per request.
    (let [res (call {:origins origins}
                    {:request-method :get
                     :headers {"origin" "http://localhost:3000"}})]
      (is (= 200 (:status res)))
      (is (= "http://localhost:3000"
             (get-in res [:headers "Access-Control-Allow-Origin"])))
      (is
       (= "Origin" (get-in res [:headers "Vary"]))
       "without Vary a shared cache could serve one origin's response to another")
      (is (= "{}" (:body res)) "the wrapped handler still runs")))
  (testing "an origin not on the list gets no CORS headers"
    (let [res (call {:origins origins}
                    {:request-method :get
                     :headers {"origin" "http://evil.example"}})]
      (is (= 200 (:status res)))
      (is (nil? (get-in res [:headers "Access-Control-Allow-Origin"])))))
  (testing "a request with no Origin is untouched"
    (let [res (call {:origins origins} {:request-method :get :headers {}})]
      (is (= 200 (:status res)))
      (is (nil? (get-in res [:headers "Access-Control-Allow-Origin"])))))
  (testing "a preflight is answered without reaching the handler"
    ;; OPTIONS arrives on a path whose route declares no OPTIONS handler,
    ;; so inside the router it would be a 404. That is why this is
    ;; middleware.
    (let [res (call {:origins origins}
                    {:request-method :options
                     :headers {"origin" "http://localhost:3000"}})]
      (is (= 204 (:status res)))
      (is (nil? (:body res)))
      (is (= "http://localhost:3000"
             (get-in res [:headers "Access-Control-Allow-Origin"])))
      (is (re-find #"Authorization"
                   (get-in res [:headers "Access-Control-Allow-Headers"]))
          "RealWorld sends its token in Authorization, so it must be allowed")
      (is (re-find #"DELETE"
                   (get-in res [:headers "Access-Control-Allow-Methods"])))))
  (testing "no origins configured means the handler is returned unchanged"
    (is (identical? handler (SUT/wrap-cors handler nil)))
    (is (identical? handler (SUT/wrap-cors handler {:origins []}))))
  (testing "options are overridable"
    (let [res (call {:origins origins
                     :methods ["GET"]
                     :request-headers ["X-Thing"]
                     :max-age 60
                     :credentials? true}
                    {:request-method :options
                     :headers {"origin" "http://localhost:3000"}})]
      (is (= "GET" (get-in res [:headers "Access-Control-Allow-Methods"])))
      (is (= "X-Thing" (get-in res [:headers "Access-Control-Allow-Headers"])))
      (is (= "60" (get-in res [:headers "Access-Control-Max-Age"])))
      (is (= "true"
             (get-in res [:headers "Access-Control-Allow-Credentials"]))))))
