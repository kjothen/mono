(ns com.repldriven.mono.bank-cash-account.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-transaction.interface :as transactions]

    [com.repldriven.mono.error.interface :as error :refer [let-nom> try-nom]]
    [com.repldriven.mono.fdb.interface :as fdb]))

(defn- enrich-account
  [config opts account]
  (let [{:keys [account-id]} account]
    (let-nom>
      [bals (when (:embed-balances opts)
              (balances/get-balances config account-id))
       txns (when (:embed-transactions opts)
              (transactions/get-transactions config account-id))]
      (cond-> account
              bals
              (merge bals)
              txns
              (assoc :transactions txns)))))

(defn get-account
  "Loads a single cash account by org-id and account-id.
  Returns the account map, nil, or anomaly. opts supports
  :embed-balances, :embed-transactions."
  [{:keys [record-db record-store] :as config} org-id account-id opts]
  (let-nom> [result (try-nom :cash-account/get
                             "Failed to load account"
                             (fdb/transact
                              record-db
                              record-store
                              "cash-accounts"
                              (fn [store]
                                (fdb/load-record store org-id account-id))))]
    (when result (enrich-account config opts (schema/pb->CashAccount result)))))

(defn get-accounts
  "Lists cash accounts for an organization. Returns
  {:accounts [maps] :before id|nil :after id|nil} or
  anomaly. opts supports :after, :before, :limit,
  :embed-balances, :embed-transactions."
  [{:keys [record-db record-store] :as config} org-id opts]
  (let-nom>
    [result
     (try-nom :cash-account/list
              "Failed to list accounts"
              (fdb/transact record-db
                            record-store
                            "cash-accounts"
                            (fn [store]
                              (fdb/scan-records
                               store
                               (merge {:prefix [org-id] :limit 100} opts)))))
     {:keys [records before after]} result]
    {:accounts (mapv (comp (partial enrich-account config opts)
                           schema/pb->CashAccount)
                     records)
     :before before
     :after after}))

(defn get-accounts-by-type
  "Returns accounts matching the given account-type.
  Uses the account_type_idx secondary index."
  [{:keys [record-db record-store]} account-type]
  (try-nom :cash-account/list-by-type
           "Failed to list accounts by type"
           (fdb/transact
            record-db
            record-store
            "cash-accounts"
            (fn [store]
              (mapv schema/pb->CashAccount
                    (fdb/query-records
                     store
                     "CashAccount"
                     "account_type"
                     (schema/account-type->pb-enum
                      account-type)))))))
