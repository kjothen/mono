(ns com.repldriven.mono.keycloak.system.components-test
  (:require
    [com.repldriven.mono.keycloak.system.components :as SUT]

    [malli.core :as m]

    [clojure.test :refer [deftest is testing]]))

(def ^:private base
  {:base-url "https://kc.invalid" :realm "queenswood"
   :admin-client-id "queenswood-admin"})

(defn- valid?
  [config]
  (m/validate (:system/config-schema SUT/identity-provider) config))

(deftest identity-provider-config-schema-test
  (testing "either credential on its own is accepted"
    (is (valid? (assoc base :admin-client-secret "s3cret")))
    (is (valid? (assoc base :admin-client-private-key-file "/keys/kc.pem"))))
  (testing "both together is accepted — the key wins at call time"
    (is (valid? (assoc base
                       :admin-client-secret "s3cret"
                       :admin-client-private-key-file "/keys/kc.pem"))))
  (testing "neither is rejected, rather than starting a client that
           cannot authenticate"
    (is (not (valid? base)))
    (is (not (valid? (assoc base
                            :admin-client-secret nil
                            :admin-client-private-key-file nil)))))
  (testing "an unrelated key passes through, so :expected-issuer still
           reaches the client"
    (is (valid? (assoc base
                       :admin-client-secret "s3cret"
                       :expected-issuer "https://public/realms/queenswood")))))
