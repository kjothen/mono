(ns com.repldriven.mono.bank-api.payment.routes
  (:require
    [com.repldriven.mono.bank-api.payment.commands :as commands]
    [com.repldriven.mono.bank-api.payment.examples :refer
     [PaymentNotFound]]
    [com.repldriven.mono.bank-api.payment.queries :as queries]
    [com.repldriven.mono.bank-api.schema :refer [ErrorResponse]]
    [com.repldriven.mono.telemetry.interface :as telemetry]))

(def routes
  [["/payments"
    {:openapi {:tags ["Payments"] :security [{"orgAuth" []}]}}
    ["/internal"
     {:post {:summary "Submit an internal payment"
             :openapi {:operationId "SubmitInternalPayment"}
             :interceptors [telemetry/require-idempotency-key]
             :parameters {:body [:ref
                                 "SubmitInternalPaymentRequest"]}
             :responses {200 {:body [:ref "InternalPayment"]}}
             :handler commands/submit-internal-payment}}]
    ["/internal/{payment-id}"
     {:parameters {:path {:payment-id string?}}}
     [""
      {:get {:summary "Retrieve an internal payment"
             :openapi {:operationId "RetrieveInternalPayment"}
             :responses {200 {:body [:ref "InternalPayment"]}
                         404 (ErrorResponse [#'PaymentNotFound])}
             :handler queries/get-internal-payment}}]]]])
