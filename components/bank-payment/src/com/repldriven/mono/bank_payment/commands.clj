(ns com.repldriven.mono.bank-payment.commands
  (:require
    [com.repldriven.mono.bank-payment.domain :as domain]

    [com.repldriven.mono.bank-schema.interface :as schema]
    [com.repldriven.mono.bank-transaction.interface :as transactions]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.fdb.interface :as fdb]))

(defn- ->response
  "Converts result to an ACCEPTED response with
  Avro-serialized payload. Returns anomalies unchanged."
  [config result]
  (if (error/anomaly? result)
    result
    (let [{:keys [schemas]} config]
      (let-nom> [payload
                 (avro/serialize (schemas "internal-payment")
                                 result)]
        {:status "ACCEPTED" :payload payload}))))

(defn submit-internal
  "Submits an internal payment. Records the underlying
  transaction and persists the payment record atomically."
  [config data]
  (->response
   config
   (let [{:keys [debtor-account-id creditor-account-id
                 currency amount reference idempotency-key]}
         data]
     (transactions/record-transaction
      config
      {:idempotency-key idempotency-key
       :transaction-type :transaction-type-internal-transfer
       :currency currency
       :reference reference
       :legs [{:account-id debtor-account-id
               :balance-type :balance-type-default
               :balance-status :balance-status-posted
               :side :leg-side-debit
               :amount amount}
              {:account-id creditor-account-id
               :balance-type :balance-type-default
               :balance-status :balance-status-posted
               :side :leg-side-credit
               :amount amount}]}
      (fn [open-store txn-result]
        (let [{:keys [transaction]} txn-result
              txn-id (:transaction-id
                      (schema/pb->Transaction transaction))
              payment (domain/new-internal-payment data
                                                   txn-id)]
          (fdb/save-record
           (open-store "internal-payments")
           (schema/InternalPayment->java payment))
          payment))))))
