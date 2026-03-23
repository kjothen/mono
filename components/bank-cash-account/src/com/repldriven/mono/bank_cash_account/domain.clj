(ns com.repldriven.mono.bank-cash-account.domain
  (:refer-clojure :exclude [name])
  (:require
    [com.repldriven.mono.encryption.interface :as encryption]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(defn- valid-currency?
  "Returns true if currency is allowed by version, rejection
  anomaly otherwise."
  [currency product]
  (let [allowed (:allowed-currencies product)]
    (if (and (seq allowed) (not (some #{currency} allowed)))
      (error/reject :cash-account/invalid-currency
                    "Currency not allowed for this product")
      true)))

(defn- valid-party?
  "Returns true if party is active, rejection anomaly
  otherwise."
  [party]
  (let [status (:status party)]
    (if (not= :party-status-active status)
      (let [s (subs (clojure.core/name status) (count "party-status-"))]
        (error/reject (keyword "cash-account"
                               (str "party-" s))
                      (str "Party is " s)))
      true)))

(defn- new-addresses
  [product address-fountain-fn]
  (let [{:keys [allowed-payment-address-schemes]} product]
    (if (empty? allowed-payment-address-schemes)
      (error/reject :cash-account/no-payment-schemes
                    "Product has no allowed payment address schemes")
      (reduce (fn [addresses scheme]
                (case scheme
                  :payment-address-scheme-scan
                  (let [sort-code "040004"
                        account-number (address-fountain-fn sort-code)]
                    (conj addresses
                          {:scheme :payment-address-scheme-scan
                           :identifier {:scan
                                        {:sort-code sort-code
                                         :account-number account-number}}}))
                  (reduced (error/reject :cash-account/unsupported-scheme
                                         (str
                                          "Unsupported payment address scheme: "
                                          (clojure.core/name scheme))))))
              []
              allowed-payment-address-schemes))))

(defn opening-account
  "Creates a new account map with status opened and payment
  addresses. Validates currency against version and party
  is active."
  [data product party _accounts address-fountain-fn]
  (let [{:keys [organization-id party-id product-id currency
                name]}
        data
        {:keys [version-id]} product]
    (let-nom>
      [_ (valid-currency? currency product)
       _ (valid-party? party)
       payment-addresses (new-addresses product address-fountain-fn)]
      (let [now (System/currentTimeMillis)]
        {:organization-id organization-id
         :party-id party-id
         :product-id product-id
         :version-id version-id
         :currency currency
         :name name
         :account-id (encryption/generate-id "acc")
         :account-status :cash-account-status-opening
         :payment-addresses payment-addresses
         :created-at now
         :updated-at now}))))

(defn opening-balances
  "Returns balances for each balance-product."
  [account-id currency balance-products]
  (let [now (System/currentTimeMillis)]
    (mapv (fn [{:keys [balance-type balance-status]}]
            {:account-id account-id
             :balance-type balance-type
             :balance-status balance-status
             :currency currency
             :credit 0
             :debit 0
             :created-at now
             :updated-at now})
          balance-products)))

(defn opened-account
  [account]
  (assoc account
         :account-status :cash-account-status-opened
         :updated-at (System/currentTimeMillis)))

(defn closing-account
  [account]
  (assoc account
         :account-status :cash-account-status-closing
         :updated-at (System/currentTimeMillis)))

(defn closed-account
  [account]
  (assoc account
         :account-status :cash-account-status-closed
         :updated-at (System/currentTimeMillis)))
