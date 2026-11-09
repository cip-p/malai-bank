# Payments are a bounded context separate from accounts, with a `payments` table that owns the lifecycle

The brief expands money-movement from "transfer between own accounts and to a handful of fictional merchants" to four send + four receive flows spanning UK and international rails. We split a new `payments-service` out of `account-service`, deep-build UK Faster Payments (FPS) end-to-end, and stub the other rails (SWIFT for international send, BACS Direct Credit for inbound, SEPA when needed) on the same plumbing. CHAPS is dropped from v1. A new top-level `payments` table owns the payment lifecycle (`PENDING`/`SETTLED`/`RETURNED` + `return_reason` + `rail` + beneficiary identifiers); the existing `transactions` and `ledger_entries` primitives are unchanged, and each lifecycle transition appends a new sum-to-zero `TransactionEntity` rather than mutating prior entries.

## Considered options

- **New `payments-service`, lifecycle on a dedicated `payments` table** *(accepted)*. Bounded-context split matches how real banks are organised (core banking vs. rails) and keeps `account-service`'s focus on accounts + ledger + directory. The `payments` row is the single authoritative state for a payment; `transactions` + `ledger_entries` remain the ledger primitives with sum-to-zero per `transaction_id` (V5) and the existing audit chain (V7) unchanged. Book Transfer (own → own at same Malai customer) gets a `Payment` row too, born `SETTLED`, so reporting is uniform.
- **Extend `account-service` and add a `state` column to `transactions`.** Smaller diff but smears the lifecycle across the ledger primitives, conflicts with the existing V5 comment ("single posted_at lifecycle; no pending/settled state"), and bakes "payments" semantics into the ledger module that should know nothing about rails. Rejected.
- **Per-rail services (`fps-service`, `swift-service`, …).** Honest to how rails differ but explodes the module list for a demo. The shared lifecycle and shared SQS plumbing make a single `payments-service` cleaner. Rejected.
- **Separate `pending_payments` table; ledger only on settle.** Gives a clean "the ledger is always settled" invariant but loses continuity — nothing in the DB survives `PENDING → SETTLED`, which makes payment-history queries awkward. Rejected.

## Routing rules

- **Book Transfer** is reserved for own → own at the **same Malai customer** (same `account_holder_id`, different `account_id`). It executes synchronously inside one DB transaction, no `PENDING`, no suspense, no return.
- **All other customer-initiated pushes are a `Payment` with a `Rail`**, including **Malai customer → another Malai customer** (rides FPS, not an on-us shortcut). This keeps a single code path in `payments-service`, exercises CoP and audit identically across destinations, and gives the demo a consistent rail story rather than "well, internal-to-internal works differently."
- The destination identifier (sort + account-number + name for UK; IBAN/BIC for international) lives on the `payments` row, *not* as a per-counterparty `accounts` row. The V14 fictional merchant `EXTERNAL` accounts (Tesco, TfL, Octopus, Spotify, ACME Employer, Demo Payee 1) are retired from `accounts` and relocated to `malai-stub`'s fictional other-bank directory — `accounts` is for malai-owned accounts only (customer accounts + the suspense accounts introduced by this work).

## API shape

`POST /api/v1/payments` is the new top-level endpoint, taking `from_account_id`, `to_sort_code`, `to_account_number`, `to_name`, `amount_minor_units`, `currency`, `description`, and an optional `rail` (inferred when omitted). The existing `POST /api/v1/accounts/{accountId}/transfers` is preserved as a thin shim that delegates to `payments-service` to avoid a same-PR breaking change for the webapp; the shim is scheduled for removal once the webapp has migrated to `/api/v1/payments`. Idempotency, audit, and ownership checks are unchanged from the existing controller patterns.

## Consequences

- A new Maven module `malai-services/payments-service` with the paired `malai-clients/malai-payments-client` and `malai-payments-client-test-support` modules. `malai-api` depends on it and imports its configuration.
- A new Flyway migration adds the `payments` table; the V14 seed is rewritten to drop the per-merchant `EXTERNAL` accounts and replace them with the suspense-account rows that ADR-0001 of `payments-service` introduces. Clean-room latitude permits replacing V14 in place; no production data exists.
- `TransfersService` in `account-service` shrinks to "book transfer only"; everything else moves to `payments-service`. Its controller stays in place (delegating) until the webapp cuts over.
- The webapp regenerates its TypeScript client from the updated `openapi.yaml`; the new `/payments` operations land alongside the existing `/transfers` ones during the cutover window.
- `CONTEXT-MAP.md` at the backend root now exists and points at `payments-service`'s `CONTEXT.md`, which defines `Payment`, `Rail`, `Suspense account`, `Settlement`, `Return`, `Available balance`, `Settled balance`, `Book transfer`, and `Lifecycle`. The webapp's existing `Transfer` term survives as the user-facing UX umbrella; backend code uses `Payment`.
