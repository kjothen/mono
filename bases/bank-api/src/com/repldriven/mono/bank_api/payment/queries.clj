(ns com.repldriven.mono.bank-api.payment.queries
  (:require
    [com.repldriven.mono.bank-api.errors :refer [error-response]]

    [com.repldriven.mono.bank-schema.interface :as schema]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]))

(defn get-internal-payment
  [request]
  (let [{:keys [record-db record-store]} request
        {:keys [payment-id]} (get-in request [:parameters :path])
        result (fdb/transact record-db
                             record-store
                             "internal-payments"
                             (fn [store]
                               (fdb/load-record store
                                                payment-id)))]
    (cond (error/anomaly? result)
          {:status 500
           :body (error-response 500 result)}
          (nil? result)
          {:status 404
           :body (error-response
                  404 "REJECTED"
                  "payment/not-found"
                  "Payment not found")}
          :else
          {:status 200
           :body (schema/pb->InternalPayment result)})))
