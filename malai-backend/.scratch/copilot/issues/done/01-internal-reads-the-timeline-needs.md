# 01 — Internal reads the Timeline needs: a payment by id, a transaction's entries

Status: ready-for-human
Category: enhancement

## What to build

Two thin internal reads, each with its client-side method and test support, so a composed reader can get from a payment id to the records that explain it. Neither exists today: `InternalPaymentsController` exposes only `POST`, and `LedgerClient` can list entries by account and window but not by transaction — even though `ledger-service` already has `entryRepository.findByTransactionIdOrderByAmountMinorUnitsAsc` internally.

**`GET /api/v1/internal/payments/{paymentId}`** on `payments-service` — the payment's own facts: state, rail, amounts and currency, beneficiary fields, purpose, created/updated moments, `endToEndId`, `screeningDecisionId`, the payer account id, and the originating instrument id where the payment came from a standing order or mandate. Service-JWT scoped like every other internal route, `@InternalEndpoint`, unknown id 404. Add the method to `MalaiPaymentsClient` and the stub to `malai-payments-client-test-support`.

**`GET /api/v1/internal/ledger/transactions/{transactionId}/entries`** on `ledger-service` — the entries of one transaction in the existing `LedgerEntryProjection` shape, plus the transaction's posted moment, type and description. 404 when the transaction is unknown. Add `listTransactionEntries` to `LedgerClient` and the stub to `malai-ledger-client-test-support`.

Neither route enters the public `openapi.yaml` — internal routes stay out of the contract, per the documented convention.

## Acceptance criteria

- [x] `GET /api/v1/internal/payments/{paymentId}` returns the payment's facts, pinned by a happy acceptance test with `noExtraProps()` at every level
- [x] Unknown payment id returns 404; a request without a service JWT returns 401 — sad acceptance tests
- [x] `GET /api/v1/internal/ledger/transactions/{transactionId}/entries` returns every entry of the transaction in the existing projection shape, ordered deterministically, pinned by a happy acceptance test
- [x] Unknown transaction id returns 404; missing service JWT returns 401 (ledger: structurally — see resolution)
- [x] `MalaiPaymentsClient` and `LedgerClient` gain the methods, each with WireMock-backed request-construction and deserialisation tests per the client-module convention
- [x] Both `*-client-test-support` modules gain the matching `With*` stub methods and response builders
- [x] Neither route appears in `openapi.yaml`
- [x] `mvn install` green

## Blocked by

Nothing.

## Resolution

Both reads landed with their client methods, test support and tests. `openapi.yaml` has a 0-line
diff and no migration changed.

**Two fields the issue asks for do not exist in the data model, and were not invented.** Confirmed
against `V15__create_payments.sql` with no later `ALTER TABLE payments`:

- **"updated moments"** — the `payments` row carries `created_at` and nothing else. A payment's
  later moments (referred, released, declined, returned) are recorded on the audit plane against
  `resourceType = "Payment"`, which is exactly what issue 02's `AuditReader` exists to read and what
  issue 06 already assumes ("the screening story comes from audit events and the payment row").
  Adding an `updated_at` column would mean touching every state transition in
  `PaymentLifecycleService` — a vertical change, not a thin read, and it would duplicate a fact the
  audit chain already holds more precisely.
- **"the originating instrument id"** — no column links a payment to its standing order or mandate.
  The link is a one-way hash: schedulers stamp
  `endToEndId = UUID.nameUUIDFromBytes("$instrumentId:$occurrenceDate")` via
  `ScheduledPaymentEndToEndId.forOccurrence`, which cannot be reversed. `endToEndId` **is** exposed,
  so Timeline assembly can go the other direction — given a standing order or mandate from its own
  client, recompute the occurrence id and match the payment. Issue 04's "the originating standing
  order or mandate where there is one" is reachable that way without a new column. **If issue 04
  wants the reverse lookup instead, that needs a migration + a populate on both scheduler paths —
  flagged, not built.**

Other decisions worth recording:

- **The ledger route carries no `@PreAuthorize`/`@InternalEndpoint`, deliberately.** `ledger-service`
  has no `malai-security` dependency at all and its acceptance boot excludes
  `SecurityAutoConfiguration` by design (`LedgerAcceptanceTest`'s KDoc says so outright: "no security
  of its own … when bundled in malai-api the aggregator's ServiceJwtFilter protects its internal
  ledger endpoints"). The new route sits behind the same shared `internalServicesFilterChain`
  (`INTERNAL_PATH_REGEX = /api/v\d+/internal/.*`) as the five ledger internal routes already there,
  so the 401 is structural. The payments route, whose module *does* have `malai-security`, asserts
  the 401 directly. Adding the annotations to ledger would have meant a new module dependency to
  restate a guarantee the chain already gives.
- **`LedgerEntryProjection` gained a nullable `accountId`**, populated only on the transaction-scoped
  read. On the account-scoped read the account is a property of the request and rides on the
  envelope; on the transaction-scoped read the legs of a double entry differ only by account and
  sign, so each entry must name its own. `spring.jackson.default-property-inclusion=non_null` means
  the account route's JSON is byte-identical to before — its five existing `noExtraProps()` tests
  pass untouched.
- **Running balance is real, not zeroed.** The new native query windows over each touched account's
  full history (inner subquery scoped by `account_id IN (…this transaction…)`), so each leg carries
  the balance that account stood at. The PRD wants balances in view deliberately.
- **Ordering** is `amountMinorUnits ASC, entryId ASC` — debit leg first, matching
  `findByTransactionIdOrderByAmountMinorUnitsAsc`, which is what the posting engine already returns
  on an idempotent replay.
- **`MalaiCode.LEDGER_TRANSACTION_NOT_FOUND(8976)`** is new — ledger-service's first 404 (its only
  prior "unknown id" case, `LEDGER_HOLD_NOT_FOUND`, is a 400). 404 was chosen to match the
  platform-wide `NotFoundException` convention the issue's AC asks for, not the local 400 precedent.
- Both routes registered in the Kensa `HttpClassifier` (after the literal `deposits-total` /
  `stopped-total` and `POST /transactions` entries, since first match wins) so the diagrams name them.

Verified: red baseline confirmed first for both routes (no handler → 404) and for the ledger 404;
the two new client tests were mutation-checked by breaking each client's URL and confirming both
fail, then reverted. `mvn install` green.
