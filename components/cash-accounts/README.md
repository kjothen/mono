# Accounts Component

Domain component for account/ledger operations.

## Command Checklist

### Account Lifecycle

- [x] open-account
- [x] close-account
- [x] reopen-account
- [x] suspend-account
- [x] unsuspend-account
- [x] archive-account

### Account Management

- [ ] update-account-details
- [ ] change-account-type
- [ ] change-account-ownership
- [ ] set-account-limits
- [ ] update-account-limits
- [ ] remove-account-limits
- [ ] link-accounts
- [ ] unlink-accounts

### Posting Operations

- [ ] post-debit
- [ ] post-credit
- [ ] reverse-debit
- [ ] reverse-credit
- [ ] adjust-debit
- [ ] adjust-credit
- [ ] batch-post-debits
- [ ] batch-post-credits

### Balance Operations

- [ ] freeze-balance
- [ ] unfreeze-balance
- [ ] partial-freeze-balance
- [ ] set-minimum-balance
- [ ] set-maximum-balance
- [ ] reserve-balance
- [ ] release-reserved-balance

### Transaction Operations

- [ ] authorize-transaction
- [ ] settle-transaction
- [ ] cancel-transaction
- [ ] reverse-transaction
- [ ] adjust-transaction
- [ ] split-transaction
- [ ] void-transaction

### Interest Operations

- [ ] calculate-interest
- [ ] post-interest
- [ ] reverse-interest
- [ ] update-interest-rate
- [ ] set-interest-calculation-method

### Fee Operations

- [ ] charge-fee
- [ ] waive-fee
- [ ] reverse-fee
- [ ] update-fee-schedule
- [ ] apply-fee-schedule

### Statement Operations

- [ ] generate-statement
- [ ] send-statement
- [ ] regenerate-statement
- [ ] request-statement-copy

### Reporting Operations

- [x] get-account-status
- [ ] get-account-balance
- [ ] get-available-balance
- [ ] get-transaction-history
- [ ] get-account-summary
- [ ] export-account-data
- [ ] get-account-audit-trail
