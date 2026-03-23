(ns com.repldriven.mono.bank-idv.watcher
  (:refer-clojure :exclude [next])
  (:require
    [com.repldriven.mono.bank-idv.domain :as domain]

    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.fdb.interface :as fdb]))

(defn party-changelog-handler
  "Returns a watcher handler that initiates IDV when a party
  is created with pending status. Watches the parties store."
  [record-store]
  (fn [ctx changelog-bytes]
    (let [changelog (schema/pb->PartyChangelog changelog-bytes)
          {:keys [organization-id party-id status-after]} changelog]
      (when (= :party-status-pending status-after)
        (let [store (record-store ctx "idvs")
              idv (domain/new-idv {:organization-id organization-id
                                   :party-id party-id})
              {:keys [verification-id status]} idv]
          (fdb/save-record store (schema/Idv->java idv))
          (fdb/write-changelog store
                               "idvs"
                               verification-id
                               (schema/IdvChangelog->pb
                                {:organization-id organization-id
                                 :verification-id verification-id
                                 :status-after status})))))))

(defn idv-changelog-handler
  "Returns a watcher handler that transitions a pending IDV
  to accepted."
  [record-store]
  (fn [ctx changelog-bytes]
    (let [changelog (schema/pb->IdvChangelog changelog-bytes)
          {:keys [organization-id verification-id status-after]} changelog]
      (when (= :idv-status-pending status-after)
        (let [store (record-store ctx "idvs")
              record (fdb/load-record store organization-id verification-id)]
          (when record
            (let [idv (schema/pb->Idv record)
                  accepted (domain/accepted-idv idv)
                  {:keys [status]} accepted]
              (fdb/save-record store (schema/Idv->java accepted))
              (fdb/write-changelog store
                                   "idvs"
                                   verification-id
                                   (schema/IdvChangelog->pb
                                    {:organization-id organization-id
                                     :verification-id verification-id
                                     :status-before status-after
                                     :status-after status})))))))))
