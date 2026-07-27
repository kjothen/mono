(ns com.repldriven.mono.auth.system
  (:require
    [com.repldriven.mono.auth.token :as token]

    [com.repldriven.mono.system.interface :as system]))

;; The signer is its config, so a started system can be inspected without
;; holding anything live — the same reason `jdbc/datasource` hands back its
;; db-spec rather than a DataSource.
;;
;; `:secret` is a required component rather than a defaulted one on purpose.
;; A signing key with a default is a signing key everyone has, and the
;; failure mode of getting it wrong is silent: tokens verify fine against
;; the wrong secret as long as everyone shares it. Supply it from config,
;; typically `!env JWT_SECRET` or the `secret` brick.
(def ^:private signer
  {:system/start (fn [{:system/keys [config instance]}] (or instance config))
   :system/config {:secret system/required-component
                   :ttl-seconds token/default-ttl-seconds}
   :system/config-schema [:map
                          [:secret string?]
                          [:ttl-seconds {:optional true} [:maybe pos-int?]]]
   :system/instance-schema map?})

(system/defcomponents :auth {:signer signer})
