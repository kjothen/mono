(ns ^:eftest/synchronized com.repldriven.mono.bank-payment.interface-test
  (:require
    com.repldriven.mono.bank-bootstrap.interface
    com.repldriven.mono.bank-payment.interface
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-organization.interface :as
     organizations]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.processor.interface :as processor]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

    [clojure.test :refer [deftest is testing]]))

(defn- send-command
  [proc schemas command-name data]
  (let [payload (avro/serialize (get schemas command-name)
                                data)]
    (if (error/anomaly? payload)
      payload
      (processor/process proc
                         {:command command-name
                          :payload payload}))))

(defn- decode-payload
  [schemas schema-name result]
  (avro/deserialize-same (get schemas schema-name)
                         (:payload result)))

(defn- fdb-config
  [sys]
  {:record-db (system/instance sys [:fdb :record-db])
   :record-store (system/instance sys [:fdb :store])})

(defn- test-submit-internal-payment
  [sys proc schemas]
  (testing "submit-internal-payment creates payment and
  records transaction"
    (let [config (fdb-config sys)
          internal (system/instance sys [:bootstrap :internal])
          internal-account-id (:account-id internal)]
      (nom-test>
        [customer-org (organizations/new-organization
                       config
                       "Payment Test Customer"
                       :organisation-type-customer
                       ["GBP"])
         customer-account-id
         (get-in customer-org
                 [:organization :accounts 0 :account-id])
         result (send-command
                 proc
                 schemas
                 "submit-internal-payment"
                 {:idempotency-key "pmt-idem-001"
                  :debtor-account-id internal-account-id
                  :creditor-account-id customer-account-id
                  :currency "GBP"
                  :amount 500
                  :reference "Test internal payment"})
         _ (is (= "ACCEPTED" (:status result)))
         decoded (decode-payload schemas
                                 "internal-payment"
                                 result)
         _ (is (some? (:payment-id decoded)))
         _ (is (= "pmt-idem-001" (:idempotency-key decoded)))
         _ (is (= internal-account-id
                  (:debtor-account-id decoded)))
         _ (is (= customer-account-id
                  (:creditor-account-id decoded)))
         _ (is (= "GBP" (:currency decoded)))
         _ (is (= 500 (:amount decoded)))
         _ (is (some? (:transaction-id decoded)))
         _ (is (= "Test internal payment"
                  (:reference decoded)))
         debtor-balance
         (balances/get-balance config
                               internal-account-id
                               :balance-type-default
                               "GBP"
                               :balance-status-posted)
         _ (is (= 500 (:debit debtor-balance)))
         creditor-balance
         (balances/get-balance config
                               customer-account-id
                               :balance-type-default
                               "GBP"
                               :balance-status-posted)
         _ (is (= 500 (:credit creditor-balance)))]))))

(defn- test-unknown-command
  [proc schemas]
  (testing "unknown command returns rejection"
    (let [result
          (send-command
           proc
           schemas
           "unknown-payment-command"
           {:idempotency-key "pmt-idem-999"
            :debtor-account-id "acc-1"
            :creditor-account-id "acc-2"
            :currency "GBP"
            :amount 100})]
      (is (error/rejection? result))
      (is (= :payment/unknown-command
             (error/kind result))))))

(deftest process-payment-test
  (with-test-system [sys "classpath:bank-payment/application-test.yml"]
                    (let [proc (system/instance sys [:payment :processor])
                          schemas (system/instance sys [:avro :serde])]
                      (test-submit-internal-payment sys proc schemas)
                      (test-unknown-command proc schemas))))
