(ns ^:eftest/synchronized com.repldriven.mono.api-keys.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface
    com.repldriven.mono.fdb.interface

    [com.repldriven.mono.api-keys.interface :as SUT]

    [com.repldriven.mono.organizations.interface :as organizations]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

    [clojure.test :refer [deftest is testing]]))

(defn- fdb-config
  [sys]
  {:record-db (system/instance sys [:fdb :record-db])
   :record-store (system/instance sys [:fdb :store])})

(deftest verify-api-key-test
  (with-test-system
   [sys "classpath:api-keys/application-test.yml"]
   (let [config (fdb-config sys)]
     (testing "round-trip: create then verify"
       (nom-test> [result (organizations/create-organization config
                                                             "Verify Org")
                   verified (SUT/verify-api-key config (:raw-key result))
                   _ (is (some? verified))
                   _ (is (= (:id (:api-key result)) (:id verified)))]))
     (testing "returns nil for unknown key"
       (is (nil? (SUT/verify-api-key config "sk_live_nonexistent")))))))

(deftest find-api-key-by-hash-test
  (with-test-system
   [sys "classpath:api-keys/application-test.yml"]
   (let [config (fdb-config sys)]
     (testing "finds key by hash"
       (nom-test> [result (organizations/create-organization config "Hash Org")
                   found (SUT/find-api-key-by-hash config
                                                   (:key-hash (:api-key
                                                               result)))
                   _ (is (some? found))
                   _ (is (= (:id (:api-key result)) (:id found)))
                   _ (is (= (:organization-id (:organization result))
                            (:organization-id found)))])))))

(deftest list-api-keys-by-org-test
  (with-test-system
   [sys "classpath:api-keys/application-test.yml"]
   (let [config (fdb-config sys)]
     (testing "lists api keys for an organization"
       (nom-test> [result (organizations/create-organization config "List Org")
                   org-id (:organization-id (:organization result))
                   keys (SUT/list-api-keys-by-org config org-id)
                   _ (is (= 1 (count keys)))
                   k (first keys)
                   _ (is (= org-id (:organization-id k)))
                   _ (is (= "default" (:name k)))
                   _ (is (string? (:key-prefix k)))])))))
