(ns com.repldriven.mono.bank-api.cash-account.routes
  (:require
    [com.repldriven.mono.bank-api.cash-account.commands :as commands]
    [com.repldriven.mono.bank-api.cash-account.queries :as queries]
    [com.repldriven.mono.bank-api.cash-account.examples :refer
     [CashAccountNotFound CashAccountAlreadyExists ProductNotPublished
      InvalidCurrency]]
    [com.repldriven.mono.bank-api.schema :refer [ErrorResponse]]
    [com.repldriven.mono.telemetry.interface :as telemetry]))

(def ^:private list-cash-accounts-query-schema
  [:map [(keyword "page[after]") {:optional true} string?]
   [(keyword "page[before]") {:optional true} string?]
   [(keyword "page[size]") {:optional true} string?]
   [(keyword "embed[balances]") {:optional true} boolean?]
   [(keyword "embed[transactions]") {:optional true} boolean?]])

(def routes
  [["/cash-accounts"
    {:openapi {:tags ["Cash Accounts"] :security [{"orgAuth" []}]}}
    [""
     {:get {:summary "Retrieve cash accounts"
            :openapi {:operationId "RetrieveCashAccounts"}
            :parameters {:query list-cash-accounts-query-schema}
            :responses {200 {:body [:ref "CashAccountList"]}}
            :handler queries/list-cash-accounts}
      :post {:summary "Open a new cash account"
             :openapi {:operationId "CreateCashAccount"}
             :interceptors [telemetry/require-idempotency-key]
             :parameters {:body [:ref "CreateCashAccountRequest"]}
             :responses {200 {:body [:ref "CreateCashAccountResponse"]}
                         422 (ErrorResponse [#'CashAccountAlreadyExists
                                             #'ProductNotPublished
                                             #'InvalidCurrency])}
             :handler commands/open-cash-account}}]
    ["/{account-id}" {:parameters {:path {:account-id [:ref "CashAccountId"]}}}
     [""
      {:get {:summary "Retrieve a cash account"
             :openapi {:operationId "RetrieveCashAccount"}
             :parameters
             {:query
              [:map [(keyword "embed[balances]") {:optional true} boolean?]
               [(keyword "embed[transactions]") {:optional true} boolean?]]}
             :responses {200 {:body [:ref "CashAccount"]}
                         404 (ErrorResponse [#'CashAccountNotFound])}
             :handler queries/get-cash-account}}]
     ["/transactions"
      {:get {:summary "Retrieve account transactions"
             :openapi {:operationId "RetrieveAccountTransactions"}
             :responses {200 {:body [:ref "TransactionList"]}}
             :handler queries/list-transactions}}]
     ["/close"
      {:post {:summary "Close a cash account"
              :openapi {:operationId "CloseCashAccount"}
              :interceptors [telemetry/require-idempotency-key]
              :responses {200 {:body [:ref "CloseCashAccountResponse"]}
                          404 (ErrorResponse [#'CashAccountNotFound])}
              :handler commands/close-cash-account}}]]]])
