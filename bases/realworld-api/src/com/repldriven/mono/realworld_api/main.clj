(ns com.repldriven.mono.realworld-api.main
  (:require
    com.repldriven.mono.auth.interface
    com.repldriven.mono.command.interface
    com.repldriven.mono.command-processor.interface
    com.repldriven.mono.jdbc.interface
    com.repldriven.mono.message-bus.interface
    com.repldriven.mono.migrator.interface
    com.repldriven.mono.realworld-store.interface
    com.repldriven.mono.server.interface

    [com.repldriven.mono.realworld-api.api :as api]

    [com.repldriven.mono.cli.interface :as cli]
    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error :refer [nom->]]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system])
  (:gen-class))

(defn start
  [config-file profile]
  (nom-> (env/config config-file profile)
         system/defs
         (assoc-in [:system/defs :server :handler] api/app)
         system/start))

(defn stop
  [system]
  (system/stop system))

(defn -main
  [& args]
  (let [{:keys [exit-message ok? config-file profile]}
        (cli/validate-args "realworld-api" args)]
    (if exit-message
      (cli/exit (if ok? 0 1) exit-message)
      (let [sys (start config-file profile)]
        (if (error/anomaly? sys)
          (cli/exit 1 (str "Failed to start: " (error/kind sys)))
          (do (log/info "realworld-api started") @(promise)))))))
