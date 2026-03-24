(ns com.repldriven.mono.bank-payment.core
  (:require
    [com.repldriven.mono.bank-payment.commands :as commands]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.processor.interface :as processor]))

(defn- dispatch
  [config message]
  (let [{:keys [command payload]} message
        {:keys [schemas]} config
        schema (get schemas command)]
    (if-not schema
      (error/fail :payment/process-command
                  {:message "No schema found for command"
                   :command command})
      (let-nom> [data (avro/deserialize-same schema payload)]
        (case command
          "submit-internal-payment"
          (commands/submit-internal config data)
          (error/reject :payment/unknown-command
                        (str "Unknown command: "
                             command)))))))

(defrecord PaymentProcessor [config]
  processor/Processor
    (process [_ message] (dispatch config message)))
