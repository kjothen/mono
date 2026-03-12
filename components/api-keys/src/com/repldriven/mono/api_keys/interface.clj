(ns com.repldriven.mono.api-keys.interface
  (:require
    [com.repldriven.mono.api-keys.domain :as domain]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.schemas.interface :as schema]))

(defn generate-api-key
  "Generates an API key and returns {:raw-key ... :key-hash
  ... :key-prefix ...}."
  []
  (domain/generate-api-key))

(defn new-api-key
  "Creates a new ApiKey record map (without the raw key)."
  [org-id api-key-name key-data]
  (domain/new-api-key org-id api-key-name key-data))

(defn find-api-key-by-hash
  "Looks up an API key by its hash. Returns the ApiKey map
  or nil."
  [{:keys [record-db record-store]} key-hash]
  (fdb/transact
   record-db
   record-store
   "api-keys"
   (fn [store]
     (first
      (map schema/pb->ApiKey
           (fdb/query-records store "ApiKey" "key_hash" key-hash))))))

(defn verify-api-key
  "Verifies a raw API key. Returns the ApiKey map if valid,
  nil otherwise."
  [config raw-key]
  (let [key-hash (domain/hash-raw-key raw-key)]
    (find-api-key-by-hash config key-hash)))

(defn list-api-keys-by-org
  "Lists all API keys for a given organization. Returns a
  sequence of ApiKey maps."
  [{:keys [record-db record-store]} org-id]
  (error/try-nom
   :api-keys/list
   "Failed to list API keys"
   (fdb/transact
    record-db
    record-store
    "api-keys"
    (fn [store]
      (mapv schema/pb->ApiKey
            (fdb/query-records
             store
             "ApiKey"
             "organization_id"
             org-id))))))
