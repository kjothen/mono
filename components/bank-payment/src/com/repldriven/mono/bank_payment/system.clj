(ns com.repldriven.mono.bank-payment.system
  (:require
    [com.repldriven.mono.bank-payment.core :as core]

    [com.repldriven.mono.system.interface :as system]))

(def ^:private processor
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (core/->PaymentProcessor config)))
   :system/config {:record-db system/required-component
                   :record-store system/required-component
                   :schemas system/required-component}
   :system/instance-schema some?})

(system/defcomponents :payment {:processor processor})
