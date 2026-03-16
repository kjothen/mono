(ns com.repldriven.mono.cash-accounts.core
  (:require
    [com.repldriven.mono.cash-accounts.commands :as commands]

    [com.repldriven.mono.processor.interface :as processor]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error]))

(defn- dispatch
  [config message]
  (let [{:keys [command payload]} message
        {:keys [schemas]} config
        schema (get schemas command)]
    (if-not schema
      (error/fail :cash-accounts/process-command
                  {:message "No schema found for command" :command command})
      (error/let-nom> [data (avro/deserialize-same schema payload)]
        (case command
          "open-cash-account" (commands/open config data)
          "close-cash-account" (commands/close config data)
          "get-cash-account" (commands/get config data)
          (error/reject :cash-accounts/unknown-command
                        (str "Unknown command: " command)))))))

(defrecord CashAccountProcessor [config]
  processor/Processor
    (process [_ message] (dispatch config message)))
