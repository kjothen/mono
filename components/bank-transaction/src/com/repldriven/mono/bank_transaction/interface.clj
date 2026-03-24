(ns com.repldriven.mono.bank-transaction.interface
  (:require
    com.repldriven.mono.bank-transaction.system

    [com.repldriven.mono.bank-transaction.commands :as commands]))

(defn record-transaction
  "Records a transaction with legs, updating balances.
  Calls f with open-store and the transaction result
  within the same FDB transaction, returning f's result."
  [config data f]
  (commands/record config data f))
