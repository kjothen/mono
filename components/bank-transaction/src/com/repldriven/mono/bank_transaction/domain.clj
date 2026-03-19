(ns com.repldriven.mono.bank-transaction.domain
  (:require
    [com.repldriven.mono.encryption.interface :as encryption]))

(defn new-transaction
  "Creates a new transaction map with status pending."
  [data]
  (let [now (System/currentTimeMillis)]
    (-> (dissoc data :legs)
        (assoc :transaction-id (encryption/generate-id "txn")
               :status :transaction-status-pending
               :created-at now
               :updated-at now))))

(defn new-leg
  "Creates a new transaction leg map from input leg data,
  linking it to the given transaction-id and currency."
  [leg transaction-id currency]
  (assoc leg
         :leg-id (encryption/generate-id "leg")
         :transaction-id transaction-id
         :currency currency
         :created-at (System/currentTimeMillis)))
