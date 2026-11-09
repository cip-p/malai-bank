# Real-payment categorisation via transaction type

Status: done

## Parent

`.scratch/spending-insights/PRD.md`

## What to build

Harden categorisation for real (non-seeded) payments, where `merchantName` is null and `description` is an opaque payment reference that no keyword rule matches. Today those fall to `GENERAL`; this slice teaches the **Categoriser** to fall back on the transaction's caller-supplied `type` and the entry sign before giving up.

Scope of this slice:
- `ledger-service`: the entries projection (both the recent and in-period reads, the projection row interface, and the `LedgerEntryProjection` / `EntriesResponse` models plus `malai-ledger-client`) gains the transaction's `type`, sourced via an additive JOIN to `transactions`. Purely additive — no column drop, no contract break.
- **Categoriser** gains a transaction-`type`/sign default tier, inserted **after** the description-keyword rules and **before** the `GENERAL` fallback:
  - a credit (`amountMinorUnits > 0`) → `INCOME`
  - a debit on a rail payment type → `PAYMENTS`
  - a book-transfer type → `TRANSFERS`
  - otherwise → `GENERAL`
- `account-service` threads the new `transactionType` from the ledger entry into the facts it sends to `insights-client.categorise(...)` and `spendingBreakdown(...)`.

## Acceptance criteria

- [x] The ledger entries projection exposes `transactionType` on both the recent and in-period reads; the `malai-ledger-client` model carries it.
- [x] An entry with no matching merchant/description keyword is categorised by type/sign: inbound credit → `INCOME`, rail-payment debit → `PAYMENTS`, book transfer → `TRANSFERS`, else `GENERAL`.
- [x] A real inbound payment credit appears as `INCOME` (and so is excluded from the spending breakdown); a real outbound rail-payment debit appears as `PAYMENTS`.
- [x] Existing keyword rules still win over the type/sign default (ordering preserved).
- [x] `ledger-service`: projection + repository tests proving `transactionType` is returned by both changed native queries.
- [x] `malai-ledger-client` tests updated for the new field.
- [x] Isolated unit tests on the **Categoriser**: the type/sign default tier for each type, and that keyword rules still take precedence.
- [x] `account-service` acceptance updated to assert `transactionType` is forwarded in the insights calls.
- [x] `mvn install` green across all modules.

## Blocked by

- `.scratch/spending-insights/issues/01-categorised-feed-tracer.md`
