(ns com.repldriven.mono.bank-api.payment.components
  (:require
    [com.repldriven.mono.bank-api.payment.examples :as examples]
    [com.repldriven.mono.bank-api.schema :refer [components-registry]]))

(def SubmitInternalPaymentRequest
  [:map
   {:json-schema/example examples/SubmitInternalPaymentRequest}
   [:idempotency-key string?]
   [:debtor-account-id string?]
   [:creditor-account-id string?]
   [:currency [:ref "Currency"]]
   [:amount int?]
   [:reference {:optional true} [:maybe string?]]])

(def InternalPayment
  [:map {:json-schema/example examples/InternalPayment}
   [:payment-id string?]
   [:idempotency-key string?]
   [:debtor-account-id string?]
   [:creditor-account-id string?]
   [:currency [:ref "Currency"]]
   [:amount int?]
   [:transaction-id string?]
   [:reference {:optional true} [:maybe string?]]
   [:created-at {:optional true} [:maybe [:ref "Timestamp"]]]
   [:updated-at {:optional true} [:maybe [:ref "Timestamp"]]]])

(def registry
  (components-registry [#'SubmitInternalPaymentRequest #'InternalPayment]))
