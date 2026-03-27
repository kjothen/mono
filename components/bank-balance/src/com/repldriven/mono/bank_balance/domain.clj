(ns com.repldriven.mono.bank-balance.domain)

(defn- net
  "Returns credit minus debit for a balance, defaulting
  to zero."
  [balance]
  (if balance (- (:credit balance 0) (:debit balance 0)) 0))

(defn- find-balance
  [balances balance-type balance-status]
  (first (filter #(and (= balance-type (:balance-type %))
                       (= balance-status (:balance-status %)))
                 balances)))

(defn posted-balance
  "Returns the net default/posted balance."
  [balances currency]
  (let [b (find-balance balances
                        :balance-type-default
                        :balance-status-posted)]
    {:value (if b (net b) 0)
     :currency currency}))

(defn available-balance
  "Returns the available balance for the given account
  type."
  [account-type balances currency]
  (let [default-posted
        (net (find-balance balances
                           :balance-type-default
                           :balance-status-posted))
        v (case account-type
            (:account-type-current
             :account-type-savings
             :account-type-term-deposit)
            (+ default-posted
               (net (find-balance
                     balances
                     :balance-type-default
                     :balance-status-pending-incoming))
               (net (find-balance
                     balances
                     :balance-type-default
                     :balance-status-pending-outgoing)))

            :account-type-settlement
            (+ default-posted
               (net (find-balance
                     balances
                     :balance-type-interest-payable
                     :balance-status-posted)))

            :account-type-internal
            default-posted

            ;; unknown type — just posted
            default-posted)]
    {:value v :currency currency}))

(defn new-balance
  "Creates a new Balance record map. Credit and debit
  default to zero if not provided."
  [{:keys [account-id balance-type balance-status currency
           credit debit]}]
  (let [now (System/currentTimeMillis)]
    {:account-id account-id
     :balance-type balance-type
     :balance-status balance-status
     :currency currency
     :credit (or credit 0)
     :debit (or debit 0)
     :created-at now
     :updated-at now}))
