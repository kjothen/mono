(ns com.repldriven.mono.bank-api.api
  (:require
    [com.repldriven.mono.bank-api.balances.components :as balances.components]
    [com.repldriven.mono.bank-api.balances.examples :as balances.examples]
    [com.repldriven.mono.bank-api.balances.routes :as balances]
    [com.repldriven.mono.bank-api.cash-account-products.components :as
     cash-account-products.components]
    [com.repldriven.mono.bank-api.cash-account-products.examples :as
     cash-account-products.examples]
    [com.repldriven.mono.bank-api.cash-account-products.routes :as
     cash-account-products]
    [com.repldriven.mono.bank-api.cash-accounts.components :as
     cash-accounts.components]
    [com.repldriven.mono.bank-api.cash-accounts.examples :as
     cash-accounts.examples]
    [com.repldriven.mono.bank-api.cash-accounts.routes :as cash-accounts]
    [com.repldriven.mono.bank-api.api-keys.components :as api-keys.components]
    [com.repldriven.mono.bank-api.api-keys.examples :as api-keys.examples]
    [com.repldriven.mono.bank-api.api-keys.routes :as api-keys]
    [com.repldriven.mono.bank-api.auth :as auth]
    [com.repldriven.mono.bank-api.examples :as examples]
    [com.repldriven.mono.bank-api.organizations.components :as
     organizations.components]
    [com.repldriven.mono.bank-api.organizations.examples :as
     organizations.examples]
    [com.repldriven.mono.bank-api.organizations.routes :as organizations]
    [com.repldriven.mono.bank-api.parties.components :as parties.components]
    [com.repldriven.mono.bank-api.parties.examples :as parties.examples]
    [com.repldriven.mono.bank-api.parties.routes :as parties]
    [com.repldriven.mono.bank-api.schema :as schema]
    [com.repldriven.mono.server.interface :as server]
    [com.repldriven.mono.telemetry.interface :as telemetry]
    [malli.core :as m]
    [malli.transform :as mt]
    [reitit.coercion.malli :as malli-coercion]
    [reitit.http :as http]
    [reitit.ring :as ring]))

(def ^:private api-transformer
  "Transformer for :decode/api and :encode/api properties
  on malli schemas. Composed with the base transformers
  to coerce API-friendly enum values to/from internal
  prefixed keywords."
  (mt/transformer {:name :api}))

(defn- ->provider
  "Creates a reitit TransformationProvider that composes
  base-transformer with api-transformer."
  [base-transformer]
  (reify malli-coercion/TransformationProvider
    (-transformer [_ {:keys [strip-extra-keys default-values]}]
      (mt/transformer
        (when strip-extra-keys (mt/strip-extra-keys-transformer))
        base-transformer
        api-transformer
        (when default-values (mt/default-value-transformer))))))

(def ^:private coercion
  (malli-coercion/create
    {:transformers {:body {:default (->provider (mt/json-transformer))},
                    :string {:default (->provider (mt/string-transformer))},
                    :response {:default (->provider nil)}}
     :options {:registry (merge (m/default-schemas)
                                {"Currency" schema/Currency,
                                 "ErrorResponse" schema/ErrorResponseSchema}
                                balances.components/registry
                                cash-account-products.components/registry
                                cash-accounts.components/registry
                                api-keys.components/registry
                                organizations.components/registry
                                parties.components/registry)}}))

(defn- routes
  [ctx]
  [["/openapi.json"
    {:get {:no-doc true,
           :openapi {:info {:title "Queenswood",
                            :description "Queenswood Banking API",
                            :version "1.0.0"},
                     :components
                       {:securitySchemes
                          {"adminAuth" {:type :http,
                                        :scheme :bearer,
                                        :description "Admin API key"},
                           "orgAuth" {:type :http,
                                      :scheme :bearer,
                                      :description "Organization API key"}},
                        :examples (merge examples/registry
                                         balances.examples/registry
                                         cash-account-products.examples/registry
                                         cash-accounts.examples/registry
                                         api-keys.examples/registry
                                         organizations.examples/registry
                                         parties.examples/registry)}},
           :handler (server/standard-openapi-handler)}}]
   (into ["/v1"
          {:interceptors (concat telemetry/trace-span
                                 (:interceptors ctx)
                                 [auth/authenticate]),
           :responses {400 (schema/ErrorResponse [#'examples/BadRequest]),
                       401 (schema/ErrorResponse [#'examples/Unauthorized]),
                       403 (schema/ErrorResponse [#'examples/Forbidden]),
                       500 (schema/ErrorResponse [#'examples/InternalServerError
                                                  #'examples/BadResponse])}}]
         (concat balances/routes
                 cash-account-products/routes
                 cash-accounts/routes
                 api-keys/routes
                 organizations/routes
                 parties/routes))])

(defn app
  [ctx]
  (http/ring-handler (http/router (routes ctx)
                                  (assoc-in server/standard-router-data
                                    [:data :coercion]
                                    coercion))
                     (ring/routes (server/standard-openapi-ui-handler)
                                  (ring/create-default-handler))
                     server/standard-executor))
