(ns com.repldriven.mono.organizations.interface
  (:require
    [com.repldriven.mono.organizations.domain :as domain]
    [com.repldriven.mono.organizations.store :as store]

    [com.repldriven.mono.api-keys.interface :as api-keys]
    [com.repldriven.mono.error.interface :as error]))

(defn new-organization
  "Creates an organization and its initial API key atomically.
  Returns {:organization <map> :api-key <map> :raw-key <string>}
  or anomaly. The raw-key is only available at creation time."
  [config org-name]
  (let [org (domain/new-organization org-name)
        {:keys [api-key raw-key]}
        (api-keys/new-api-key (:organization-id org) "default")]
    (error/let-nom> [_ (store/create config org api-key)]
      {:organization org
       :api-key api-key
       :raw-key raw-key})))

(defn get-organizations
  "Lists organizations. Returns a sequence of organization
  maps or anomaly."
  [config]
  (store/get-organizations config))
