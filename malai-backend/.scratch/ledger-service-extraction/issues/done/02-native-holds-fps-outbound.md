# Native Holds + FPS outbound (place / settle / return)

Status: done

## Progress note — slice 1 (ledger Hold primitive) DONE; slice 2 (payments cutover) DONE

Split into two slices to keep the demo green at each commit. **Both slices are implemented,
tested, and committed.** Slice 1 in `35a2d3b`; slice 2 in this commit.

Done (slice 2 — `payments-service` FPS-outbound cutover onto holds):
- `PaymentsService.initiatePayment` branches by rail. FPS → `placeOutboundHold` (`ledgerClient.placeHold`,
  holdId = paymentId), dropping the `balanceAsOf` pre-check and the `customer → suspense` posting;
  `LedgerInsufficientFundsException` → `PAYMENT_INSUFFICIENT_FUNDS`. SWIFT keeps the suspense path
  (`postOutboundSuspenseDebit`, incl. the `balanceAsOf` guard — issue 03 moves it). Book transfer unchanged.
- `PaymentLifecycleService` gains `LedgerClient` + `PaymentsServiceTokenClient`. `transitionToSettled`
  FPS → `settleViaHold`: `settleHold(holdId, customer → SCHEME_SETTLEMENT)` + a `SCHEME_SETTLEMENT →
  beneficiary` `postTransaction` when the beneficiary is on-us. `transitionToReturned` FPS →
  `releaseViaHold` (`releaseHold`, posts nothing). SWIFT keeps `settleViaSuspense`/`returnViaSuspense`.
- `FpsOutboundSettledListener` / `FpsOutboundReturnedListener` now take `Message<T>` and propagate the
  `TestId` header to `TestIdContext` (matching `FpsInboundReceivedListener`), so the async outgoing
  ledger calls are capturable in acceptance tests. Production no-op when `TestId` is unset.
- V14 seed: the FPS outbound suspense account (`...010001`) is removed from both the `accounts` insert
  and the `ledger.ledger_accounts` registry. `SCHEME_SETTLEMENT` (`...010002`) stays seeded + registered.
- FPS-outbound acceptance tests (happy/external/return-reasons/sad) re-pointed onto `WithLedger`
  WireMock fixtures; outgoing place/settle/release/credit calls asserted via
  `paymentsServiceMadeTheFollowingHttpRequest`; the local `ledger.ledger_entries` assertions are gone.
  Green: payments-service 66 tests (FPS outbound happy 13 / sad 3, SWIFT + book-transfer + inbound unchanged).

## Progress note — slice 1 (ledger Hold primitive) DONE; slice 2 (payments cutover) DONE

Split into two slices to keep the demo green at each commit. **Slice 1 is implemented, tested,
and committed** (`35a2d3b`, alongside the issue-01 foundation code that had been left uncommitted).

Done (slice 1 — the Hold primitive in `ledger-service` + client):
- `ledger.holds` table in V5; `HoldEntity` / `HoldRepository.heldAmountOf`.
- `HoldManager`: `place` (Available-guarded, account-locked, idempotent on hold id), `settle`
  (atomic mark-SETTLED + balanced debit via `PostingEngine` in one tx — Available never blips;
  idempotent), `release` (posts nothing). Lifecycle `PLACED → SETTLED | RELEASED`; cross-state →
  `LEDGER_HOLD_ALREADY_RESOLVED`, unknown hold → `LEDGER_HOLD_NOT_FOUND`.
- `PostingEngine` funds guard now checks **Available = ledger − held** (no-op when no holds).
- `BalanceService.getBalances` {ledger/held/available}; `LedgerHoldsController` (`POST /holds`,
  `/holds/{id}/settle`, `/holds/{id}/release`) + `LedgerBalancesController`
  (`GET /accounts/{id}/balances`).
- `malai-ledger-client`: `placeHold` (422 → `LedgerInsufficientFundsException`), `settleHold`,
  `releaseHold`, `getBalances` + models; `WithLedger` stubs + `HoldResponseBuilder` /
  `SettleHoldResponseBuilder` / `BalancesResponseBuilder`.
- Green: ledger-service 20 (holds happy 6 / sad 5 / balances 2 + 7 existing), ledger-client 8;
  account-service + payments-service unchanged and still green.

Remaining (slice 2 — `payments-service` FPS-outbound cutover onto holds):
- initiate FPS → `placeHold` on the payer (drop the `balanceAsOf` pre-check + the
  `customer → suspense` posting); map `LedgerInsufficientFundsException` → `PAYMENT_INSUFFICIENT_FUNDS`.
- settle FPS → `settleHold` (`customer → SCHEME_SETTLEMENT`) + a `SCHEME_SETTLEMENT → beneficiary`
  `postTransaction` when the beneficiary is on-us; return FPS → `releaseHold` (post nothing).
  Branch the lifecycle by rail so **SWIFT stays on the old suspense path** (issue 03 moves it).
- Use the payment id as the hold id. Re-point the FPS-outbound acceptance tests
  (happy/external/return-reasons/sad) at `WithLedger` WireMock fixtures instead of local
  `ledger.ledger_entries` assertions. Retire the FPS outbound suspense seed row (V14) once nothing
  posts to it (`SCHEME_SETTLEMENT` 010002 is already seeded + registered).
- The SQS consumers are already correct (`FpsOutboundSettledConsumer` → `transitionToSettled`,
  `FpsOutboundReturnedConsumer` → `transitionToReturned`); only what those `PaymentLifecycleService`
  methods *do* for FPS changes.

Ops gotcha: after schema edits, re-migrate `malai_test` — but `flyway.clean()` only cleans the
`public` schema, NOT `ledger`, so a clean rebuild leaves `ledger.*` behind and V5 re-fails with
"relation transactions already exists". Reset reliably with
`docker exec malai-postgres psql -U malai -d malai_test -c "DROP SCHEMA IF EXISTS ledger CASCADE; DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;"`
then run the migrator **without** `MALAI_DB_CLEAN`. (Worth teaching `MalaiDbMigrator` to clean the
`ledger` schema too — small dev-infra follow-up.)

## Parent

`.scratch/ledger-service-extraction/PRD.md`

## What to build

Introduce the **Hold** as a first-class ledger primitive and move FPS outbound onto it — the deep rail with the full place / settle / return surface. This is where "block an amount, settle afterwards" becomes a real ledger capability and where **Available balance** starts diverging from **Ledger balance**.

Scope:
- **Hold manager** deep module + `holds` table (`hold_id`, `account_id`, `amount_minor_units`, `status`, `placed_at`, `resolved_at`): `placeHold` (funds-guarded on Available, idempotent), `settleHold` (atomic post-and-release: mark `SETTLED` + record the balanced debit posting in one transaction, full-amount only), `releaseHold` (posts nothing). Lifecycle `PLACED → SETTLED | RELEASED`, no expiry.
- `getBalances(accountId)` returning `{ ledgerBalance, heldAmount, availableBalance }`; the posting/hold funds guard now checks Available = ledger − held.
- Seed + register `SCHEME_SETTLEMENT` as a `SYSTEM` / `ALLOW_NEGATIVE` account; retire the FPS **outbound** suspense account.
- `payments-service` FPS outbound cutover: initiate → `placeHold` on the payer; settle → `settleHold` posting `customer → SCHEME_SETTLEMENT` (plus a `SCHEME_SETTLEMENT → beneficiary` credit `postTransaction` when the beneficiary is on-us); return → `releaseHold`. The `payments` row stays the lifecycle authority and saga coordinator. Outbound suspense posting logic is deleted; `transitionToReturned` posts nothing.

## Acceptance criteria

- [ ] `placeHold` reduces **Available balance** without changing **Ledger balance**; a hold that would breach Available is rejected with `LEDGER_INSUFFICIENT_FUNDS`; two concurrent holds cannot reserve the same money.
- [ ] `settleHold` marks the hold `SETTLED` and records the balanced debit in one transaction (Available never blips); `releaseHold` frees the reservation and records no ledger entries; both are idempotent on `holdId`.
- [ ] `getBalances` returns ledger/held/available consistent with the active holds and postings.
- [ ] An FPS outbound payment places a hold on send, debits the payer to `SCHEME_SETTLEMENT` on settle (crediting an on-us beneficiary), and on return leaves **zero ledger entries** while restoring the payer's available funds.
- [ ] The FPS outbound suspense account is gone from the seed; `SCHEME_SETTLEMENT` is seeded and registered.
- [ ] Kensa acceptance tests cover place/settle/release (lifecycle, guard on place, atomic settle, idempotent replay) and `getBalances`, with full-entity DB assertions; the FPS-outbound cutover asserts the outgoing ledger calls via `WithLedger` fixtures, including the `LEDGER_INSUFFICIENT_FUNDS` rejection path.
- [ ] The demo (send FPS, watch Available drop, settle and return) is green.

## Blocked by

- `.scratch/ledger-service-extraction/issues/01-foundation-ledger-schema-registry-posting-book-transfer.md`
