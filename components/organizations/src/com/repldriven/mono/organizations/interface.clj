(ns com.repldriven.mono.organizations.interface
  (:require
    [com.repldriven.mono.organizations.domain :as domain]

    [com.repldriven.mono.api-keys.interface :as api-keys]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.schemas.interface :as schema]))

(defn create-organization
  "Creates an organization and its initial API key atomically.
  Returns {:organization <map> :api-key <map> :raw-key <string>}
  or anomaly. The raw-key is only available at creation time."
  [{:keys [record-db record-store]} org-name]
  (let [org (domain/new-organization org-name)
        key-data (api-keys/generate-api-key)
        api-key (api-keys/new-api-key
                 (:organization-id org)
                 "default"
                 key-data)]
    (error/let-nom> [_ (fdb/transact-multi
                        record-db
                        record-store
                        (fn [open-store]
                          (let [org-store (open-store "organizations")
                                key-store (open-store "api-keys")]
                            (fdb/save-record
                             org-store
                             (schema/Organization->java org))
                            (fdb/save-record
                             key-store
                             (schema/ApiKey->java
                              api-key)))))]
      {:organization org
       :api-key api-key
       :raw-key (:raw-key key-data)})))
