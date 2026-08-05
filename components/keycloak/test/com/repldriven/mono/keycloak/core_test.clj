(ns com.repldriven.mono.keycloak.core-test
  (:require
    [com.repldriven.mono.keycloak.core :as SUT]

    [buddy.core.keys :as buddy-keys]
    [buddy.sign.jwt :as jwt]

    [clojure.java.io :as io]
    [clojure.test :refer [deftest is testing]])
  (:import
    (java.security KeyPairGenerator)
    (java.util Base64)))

;; The brick reads the signing key from a PEM file, so the round-trip
;; test needs a real one on disk rather than an in-memory key.
(defn- write-key-pair!
  []
  (let [generator (doto (KeyPairGenerator/getInstance "RSA")
                    (.initialize 2048))
        key-pair (.generateKeyPair generator)
        encoder (Base64/getMimeEncoder 64 (.getBytes "\n" "UTF-8"))
        pem (str "-----BEGIN PRIVATE KEY-----\n"
                 (.encodeToString encoder (.getEncoded (.getPrivate key-pair)))
                 "\n-----END PRIVATE KEY-----\n")
        file (doto (java.io.File/createTempFile "keycloak-test-key" ".pem")
               (.deleteOnExit))]
    (spit file pem)
    {:path (.getAbsolutePath file) :public-key (.getPublic key-pair)}))

(deftest client-assertion-claims-test
  (let [now-ms 1750000000000
        claims (SUT/client-assertion-claims {:client-id "queenswood-admin"
                                             :token-url "https://kc/token"
                                             :jti "a-jti"
                                             :now-ms now-ms})]
    (testing "the client authenticates as itself"
      (is (= "queenswood-admin" (:iss claims)))
      (is (= "queenswood-admin" (:sub claims))))
    (testing "the audience is the token endpoint"
      (is (= "https://kc/token" (:aud claims))))
    (testing "claims are epoch seconds, not the millis they came from"
      (is (= (quot now-ms 1000) (:iat claims))))
    (testing "the assertion is short-lived"
      (is (= (quot SUT/client-assertion-ttl-ms 1000) (- (:exp claims)
                                                        (:iat claims)))))
    (testing "a jti is carried so Keycloak can reject a replay"
      (is (= "a-jti" (:jti claims))))))

(deftest client-assertion-signing-test
  (testing "an assertion signed from a PEM file verifies against its
           public key"
    (let [{:keys [path public-key]} (write-key-pair!)
          claims (SUT/client-assertion-claims
                  {:client-id "queenswood-admin"
                   :token-url "https://kc/token"
                   :jti "a-jti"
                   :now-ms (System/currentTimeMillis)})
          token (jwt/sign claims (buddy-keys/private-key path) {:alg :rs256})]
      (is (string? token))
      (is (= "queenswood-admin"
             (:iss (jwt/unsign token public-key {:alg :rs256})))))))

(deftest client-assertion-type-test
  (testing "the assertion type is the RFC 7523 URN Keycloak expects"
    (is (= "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
           SUT/client-assertion-type))))

(deftest missing-key-file-test
  (testing "a key file that is not there fails rather than throwing"
    (is (thrown? Exception
                 (buddy-keys/private-key
                  (str (io/file "does-not-exist" "key.pem")))))))
