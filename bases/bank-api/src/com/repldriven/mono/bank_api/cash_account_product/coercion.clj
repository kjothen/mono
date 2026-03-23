(ns com.repldriven.mono.bank-api.cash-account-product.coercion
  (:require
    [com.repldriven.mono.bank-api.coercion :as coercion]))

(def ^:private account-type-enum
  (coercion/enum-coercion {"internal" :account-type-internal
                           "settlement" :account-type-settlement
                           "current" :account-type-current
                           "savings" :account-type-savings
                           "term-deposit" :account-type-term-deposit}
                          :account-type-unknown))

(def ^:private balance-sheet-side-enum
  (coercion/enum-coercion {"asset" :balance-sheet-side-asset
                           "liability" :balance-sheet-side-liability}
                          :balance-sheet-side-unknown))

(def ^:private payment-address-scheme-enum
  (coercion/enum-coercion {"scan" :payment-address-scheme-scan
                           "iban" :payment-address-scheme-iban
                           "swift" :payment-address-scheme-swift
                           "ach" :payment-address-scheme-ach}
                          :payment-address-scheme-unknown))

(def account-type-enum-schema (:enum-schema account-type-enum))
(def balance-sheet-side-enum-schema (:enum-schema balance-sheet-side-enum))
(def payment-address-scheme-enum-schema
  (:enum-schema payment-address-scheme-enum))
