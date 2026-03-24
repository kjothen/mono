(ns com.repldriven.mono.bank-transaction.interface
  (:require
    com.repldriven.mono.bank-transaction.system

    [com.repldriven.mono.bank-transaction.commands :as commands]
    [com.repldriven.mono.bank-transaction.store :as store]))

(defn record-transaction
  "Records a transaction with legs, updating balances.
  Calls f with open-store and the transaction result
  within the same FDB transaction, returning f's result."
  [config data f]
  (commands/record config data f))

(defn get-account-transactions
  "Returns transaction legs for an account, enriched
  with parent transaction type, status, and reference."
  [config account-id]
  (store/get-account-transactions config account-id))
