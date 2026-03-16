(ns com.repldriven.mono.cash-accounts.watcher
  (:require
    [com.repldriven.mono.cash-accounts.domain :as domain]

    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.schemas.interface :as schema]))

(defn cash-account-changelog-handler
  "Returns a watcher handler that transitions closing
  accounts to closed when their changelog reports
  status-after :closing."
  [accounts-store-fn]
  (fn [ctx changelog-bytes]
    (let [changelog (schema/pb->CashAccountChangelog
                     changelog-bytes)]
      (when (= :closing (:status-after changelog))
        (let [store (accounts-store-fn ctx "cash-accounts")
              account-id (:account-id changelog)
              organization-id (:organization-id changelog)]
          (when-some [rec (fdb/load-record store
                                           organization-id
                                           account-id)]
            (let [account (schema/pb->CashAccount rec)]
              (when-some [transitioned
                          (domain/transition-lifecyle
                           store
                           account)]
                (fdb/save-record
                 store
                 (schema/CashAccount->java transitioned))
                (fdb/write-changelog
                 store
                 "cash-accounts"
                 (:account-id transitioned)
                 (schema/CashAccountChangelog->pb
                  {:account-id account-id
                   :status-before
                   (:account-status account)
                   :status-after
                   (:account-status transitioned)}))))))))))
