(ns com.repldriven.mono.bank-api.cash-accounts.components
  (:require
    [com.repldriven.mono.bank-api.cash-accounts.examples
     :as examples]

    [com.repldriven.mono.bank-api.schema
     :refer [components-registry]]))

(def CashAccountId
  [:string {:title "CashAccountId"
            :json-schema/example examples/CashAccountId}])

(def ScanAddress
  [:map
   [:sort-code string?]
   [:account-number string?]])

(def PaymentAddress
  [:map
   [:scheme string?]
   [:identifier {:optional true}
    [:maybe
     [:map
      [:scan {:optional true}
       [:maybe [:ref "ScanAddress"]]]
      [:value {:optional true}
       [:maybe string?]]]]]])

(def CashAccount
  [:map
   {:json-schema/example examples/CashAccount}
   [:organization-id {:optional true} [:maybe string?]]
   [:account-id [:ref "CashAccountId"]]
   [:party-id string?]
   [:name string?]
   [:currency [:ref "Currency"]]
   [:product-id string?]
   [:version-id string?]
   [:account-status [:enum :opening :opened :closing :closed]]
   [:payment-addresses {:optional true}
    [:maybe [:vector [:ref "PaymentAddress"]]]]
   [:created-at {:optional true} [:maybe string?]]
   [:updated-at {:optional true} [:maybe string?]]])

(def CreateCashAccountRequest
  [:map
   {:json-schema/example examples/CreateCashAccountRequest}
   [:party-id string?]
   [:name string?]
   [:currency [:ref "Currency"]]
   [:product-id string?]])

(def CreateCashAccountResponse [:ref "CashAccount"])

(def CashAccountList
  [:map
   {:json-schema/example examples/CashAccountList}
   [:cash-accounts
    [:vector [:ref "CashAccount"]]]
   [:links {:optional true}
    [:map
     [:next {:optional true} string?]
     [:prev {:optional true} string?]]]])

(def CloseCashAccountResponse [:ref "CashAccount"])

(def registry
  (components-registry
   [#'CashAccountId #'ScanAddress #'PaymentAddress
    #'CashAccount #'CreateCashAccountRequest
    #'CreateCashAccountResponse #'CashAccountList
    #'CloseCashAccountResponse]))
