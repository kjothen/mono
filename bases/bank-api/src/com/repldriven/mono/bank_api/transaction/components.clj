(ns com.repldriven.mono.bank-api.transaction.components
  (:require
    [com.repldriven.mono.bank-api.transaction.coercion :as
     coercion]
    [com.repldriven.mono.bank-api.schema :refer
     [components-registry]]))

(def TransactionStatus
  (coercion/transaction-status-enum-schema {:json-schema/example "posted"}))

(def TransactionType
  (coercion/transaction-type-enum-schema {:json-schema/example
                                          "internal-transfer"}))

(def LegSide (coercion/leg-side-enum-schema {:json-schema/example "debit"}))

(def Transaction
  [:map
   [:leg-id string?]
   [:transaction-id string?]
   [:account-id string?]
   [:balance-type [:ref "BalanceType"]]
   [:balance-status [:ref "BalanceStatus"]]
   [:side [:ref "LegSide"]]
   [:amount int?]
   [:currency string?]
   [:transaction-type {:optional true}
    [:maybe [:ref "TransactionType"]]]
   [:status {:optional true}
    [:maybe [:ref "TransactionStatus"]]]
   [:reference {:optional true} [:maybe string?]]
   [:created-at {:optional true}
    [:maybe [:ref "Timestamp"]]]])

(def TransactionList
  [:map
   [:transactions [:vector [:ref "Transaction"]]]])

(def registry
  (components-registry [#'TransactionStatus #'TransactionType #'LegSide
                        #'Transaction #'TransactionList]))
