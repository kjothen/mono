(ns com.repldriven.mono.bank-cash-account.watcher
  (:require
    [com.repldriven.mono.bank-cash-account.domain :as domain]

    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.fdb.interface :as fdb]))

(defn cash-account-changelog-handler
  "Returns a watcher handler that transitions an opening account 
  to opened, or a closing account to closed."
  [accounts-store]
  (fn [ctx changelog-bytes]
    (let [changelog (schema/pb->CashAccountChangelog changelog-bytes)
          {:keys [organization-id account-id status-after]} changelog]
      (when (#{:cash-account-status-opening :cash-account-status-closing}
             status-after)
        (let [store (accounts-store ctx "cash-accounts")
              record (fdb/load-record store organization-id account-id)]
          (when record
            (let [account (schema/pb->CashAccount record)
                  transitioned
                  (case status-after
                    :cash-account-status-opening (domain/opened-account account)
                    :cash-account-status-closing (domain/closed-account account)
                    :else account)
                  {:keys [account-status]} transitioned]
              (fdb/save-record store (schema/CashAccount->java transitioned))
              (fdb/write-changelog store
                                   "cash-accounts"
                                   account-id
                                   (schema/CashAccountChangelog->pb
                                    {:account-id account-id
                                     :status-before status-after
                                     :status-after account-status})))))))))







