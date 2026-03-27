(ns com.repldriven.mono.bank-transaction.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :refer [try-nom]]
    [com.repldriven.mono.fdb.interface :as fdb]))

(defn get-transactions
  "Returns transaction legs for an account, enriched with
  the parent transaction's type, status, and reference.
  Queries the TransactionLeg_by_account index."
  [{:keys [record-db record-store]} account-id]
  (try-nom
   :transaction/list
   "Failed to list account transactions"
   (fdb/transact-multi
    record-db
    record-store
    (fn [open-store]
      (let [leg-store (open-store "transaction-legs")
            txn-store (open-store "transactions")
            legs (mapv schema/pb->TransactionLeg
                       (fdb/query-records leg-store
                                          "TransactionLeg"
                                          "account_id"
                                          account-id))]
        (mapv (fn [leg]
                (let [txn-record
                      (fdb/load-record txn-store
                                       (:transaction-id leg))
                      txn (when txn-record
                            (schema/pb->Transaction
                             txn-record))]
                  (merge leg
                         (select-keys txn
                                      [:transaction-type
                                       :status
                                       :reference]))))
              legs))))))
