(ns com.repldriven.mono.realworld-store.system
  (:require
    [com.repldriven.mono.system.interface :as system]))

;; Instance-is-config, as `jdbc/datasource` does: the store is a handle to
;; things rather than a thing, so a started system stays inspectable as data.
;;
;; `:datasource` is expected to be wired to `migrator.migrations` rather than
;; to `jdbc.datasource` directly. The migrator component's instance is the
;; datasource it migrated, so referencing it is what orders schema creation
;; before first query — there is no other ordering signal between them.
(def ^:private store
  {:system/start (fn [{:system/keys [config instance]}] (or instance config))
   :system/config {:datasource system/required-component
                   :signer system/required-component}
   :system/config-schema [:map [:datasource some?] [:signer some?]]
   :system/instance-schema map?})

(system/defcomponents :realworld-store {:store store})
