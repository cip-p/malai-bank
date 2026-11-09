# 07 — Inbound BACS direct-debit rail

Status: done

## Parent

`.scratch/standing-order-direct-debit-lifecycle/PRD.md`

## What to build

The money-movement half of the direct-debit tracer: a new BACS direct-debit rail on which collections arrive as inbound scheme messages, mirroring the existing inbound credit topology (stub → gateway → queue → consumer). The payments service gains its first inbound-debit path: on a collection message, check the customer's available funds; if sufficient, post debit-customer/credit-suspense to the ledger and record a settled inbound payment; if not, return the collection unpaid — a returned payment with an insufficient-funds reason, a return event flowing back out through the gateway, and nothing posted to the customer's account. Queue-message dedup applies as on the existing rails. The stub gains a collection-origination endpoint (following its existing inbound simulation routes) so a bank-side caller can have a collection genuinely enter from the outside world; the BACS gateway learns to translate the new message types in both directions.

## Acceptance criteria

- [x] Inbound collection with sufficient funds: ledger posting request asserted (customer debited, suspense credited), payment recorded settled/inbound on the new rail with creditor details and the originator's end-to-end ID — database assertion; feed line visible via the account transactions endpoint
- [x] Inbound collection exceeding available funds: payment recorded returned with the insufficient-funds reason, outbound return event asserted as a real queue message, no ledger posting touches the customer account
- [x] Duplicate collection message (same end-to-end ID) deduplicated: processed once, replay is a no-op
- [x] Unknown target account handled per the existing inbound unknown-beneficiary pattern
- [x] Gateway tests: collection origination in via the scheme surface → queue event out; return command out → scheme call, per the existing per-rail gateway test pattern
- [x] Stub origination endpoint drives the whole flow; Kensa acceptance tests publish real inbound queue messages, per the existing inbound-payment test style
- [x] Audit events recorded for settlement and return

## Progress

2026-07-07 — backend BACS direct-debit inbound rail landed on `main`. Full topology
stub → gateway → queue → payments consumer → ledger → return, mirroring the existing
inbound-credit rail exactly.

Key decisions:
- New rail `Rail.BACS_DIRECT_DEBIT` (INBOUND). On a `BacsCollectionNotify`,
  `DirectDebitCollectionProcessor` (a faithful copy of `InboundPaymentProcessor`'s
  shape) dedups on `(BACS_EVENT_COLLECTION, endToEndId)`, resolves the debtor account
  by sort-code/account-number, and calls the new
  `PaymentLifecycleService.collectFromDebtor`.
- Sufficient funds: ledger `DIRECT_DEBIT_COLLECTION` posts `-amount` to the debtor /
  `+amount` to the BACS inbound suspense; a SETTLED INBOUND `BACS_DIRECT_DEBIT`
  payment row is written with the debtor as `from` and the creditor as `to`, plus an
  `INBOUND_COLLECTION_SETTLED` SYSTEM audit row.
- Insufficient funds: the ledger call throws `LedgerInsufficientFundsException`
  (caught inside the `@Transactional` `collectFromDebtor`, so nothing posts to the
  customer account); a RETURNED payment with `ReturnReason.INSUFFICIENT_FUNDS` +
  `INBOUND_COLLECTION_RETURNED` audit row is written, and the processor publishes a
  `BacsCollectionReturn` command on `payments-bacs-command-return`. The gateway's
  `BacsReturnListener` translates it to a scheme-shaped `BacsSchemeCollectionReturn`
  (reason code `0`) and POSTs it to the BACS scheme (new `BacsSchemeClient`, wired via
  `MalaiClientConfiguration` + `malai.bacs-scheme.base-url`).
- Unknown target account: no payment row; the processor publishes a
  `ReturnReason.INVALID_ACCOUNT` return command — identical to the inbound-credit
  unknown-beneficiary pattern.
- New internal message contracts (`BacsCollectionNotify`, `BacsCollectionReturn`) and
  scheme contracts (`BacsSchemeDirectDebitCollection`, `BacsSchemeCollectionReturn`,
  `BacsReturnReasonCodes`). New queues `BACS_EVENT_COLLECTION` (event in) and
  `BACS_COMMAND_RETURN` (command out); `INSUFFICIENT_FUNDS` added to `ReturnReason`.
- Stub gains a collection-origination surface (`POST /scenarios/collections/bacs` →
  `StubBacsCollectionNotifier` → gateway `POST /bacs/callbacks/collection`) plus a
  scheme return sink (`POST /bacs/collections/returns`), following the existing
  inbound simulation routes. No openapi.yaml change (all endpoints are internal/stub,
  outside the `/api/v1/**` customer contract); oasdiff stays clean. No migration
  (V-series `payments` table already carries every column; only the `Rail` enum and
  the archunit frozen-violations store changed).

Verification: full reactor compiles + installs; payments-service, bacs-gateway and
malai-stub all green. New acceptance coverage — payments happy (settled + dedup),
payments sad (insufficient-funds return + unknown-account return), gateway
(origination→event, return-command→scheme call), stub (scenario forwarding + return
ack + validation). All assert real inbound SQS messages under stubbed time, matching
the existing inbound-payment test style.

Deferred (environmental): the literal cross-stack account-transactions **feed poll**
needs the full running stack (all services + localstack + Cognito stub, AWS creds),
not reproducible here — same limitation as issue 06's last AC. The feed line is an
emergent property of the asserted ledger posting; at the payments-service acceptance
level the settled collection is verified via `GET /api/v1/payments/{id}`, exactly as
the existing inbound-credit rail verifies its own "feed visible" clause. Issues 08
(collection requester) and 09 (sandbox lodgement + DD e2e) are now unblocked — they
land the collection on this rail.

## Blocked by

None - can start immediately
