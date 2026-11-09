# `payments-service` foundation + Book Transfer via `POST /api/v1/payments`

Status: ready-for-agent

## Parent

`.scratch/send-and-receive-money/PRD.md`

## What to build

Stand up the new `payments-service` bounded context and its `payments` table, and serve **Book Transfer** (own → own at the same Malai customer) through a new `POST /api/v1/payments` endpoint. After this slice ships, an authenticated customer can move money from their own Account A to their own Account B by calling `POST /api/v1/payments` with `fromAccountId` + `toSortCode` + `toAccountNumber` (resolving to a destination they own); a row appears in `payments` with `rail = BOOK_TRANSFER` and `state = SETTLED`; the existing `transactions` + `ledger_entries` rows are written exactly as `TransfersService` writes them today; the audit chain records the expected event; the response includes `paymentId`, `state`, `rail`, `createdAt`, `endToEndId`.

The existing `POST /api/v1/accounts/{accountId}/transfers` endpoint and `TransfersService` continue to work unchanged — both paths exist in parallel. The webapp is unaffected. CoP, FPS, suspense accounts, SQS, and `malai-stub` are all out of scope for this slice and land in #02 and beyond.

`Book Transfer`-eligible means: both source and destination resolve to accounts under the same `account_holder_id`. If the destination resolves to a *different* `account_holder_id` (another Malai customer) or to a non-Malai destination (any sort+account-number the local directory doesn't recognise), the new endpoint returns `BAD_REQUEST` with a clear "rail not yet supported" code for now — that gap closes in #02 and #04. The point of this slice is to land the shape, not the breadth.

The `payments` table is created with the full set of columns described in the PRD's "Implementation Decisions / Schema changes" section, even though Book Transfer only populates a subset — adding columns later via Flyway is cheap, but reshaping a table the lifecycle code will soon write to is expensive. International-only columns (`debtor_iban`, `debtor_bic`, `beneficiary_iban`, `beneficiary_bic`, `payment_purpose`) are nullable from the start.

OpenAPI is updated first per the backend's "edit `openapi.yaml` first" rule. The new operation lives under a new `Payments` tag.

Domain glossary terms used: `Payment`, `Book Transfer`, `Rail`, `Settlement`, `Lifecycle`. Avoid synonyms the glossary flags.

## Acceptance criteria

- [ ] New Maven modules registered in root `pom.xml` (both `<modules>` and `<dependencyManagement>`): `malai-services/payments-service`, `malai-clients/malai-payments-client`, `malai-clients/malai-payments-client-test-support`.
- [ ] `malai-api/pom.xml` depends on `payments-service` and imports its `@Configuration`.
- [ ] New Flyway migration adds the `payments` table with all columns from the PRD schema, indexes on `(beneficiary_account_id, state, created_at DESC)` and `(debtor_account_id, state, created_at DESC)`, and a unique constraint on `end_to_end_id`.
- [ ] `PaymentsController` exposes `POST /api/v1/payments` and `GET /api/v1/payments/{paymentId}`, secured the same way as existing controllers (`@PreAuthorize("hasRole('CUSTOMER')")` + `@AuthenticationPrincipal MalaiUserWithAccountHolderId`).
- [ ] The Book Transfer path writes exactly one `Payment` row (`rail = BOOK_TRANSFER`, `state = SETTLED`), one `TransactionEntity`, and two balanced `LedgerEntryEntity` rows — sum-to-zero per `transaction_id` is preserved.
- [ ] Audit event emitted within the same `@Transactional` as the ledger writes; existing chain-lock posture unchanged.
- [ ] Ownership enforced: a customer cannot Book Transfer from an account that doesn't belong to their `account_holder_id`; cross-tenant access returns 404, not 403.
- [ ] Non-Book-Transfer requests (cross-customer at Malai, or external destinations) return `BAD_REQUEST` with a "rail not yet supported" malai code. Tests cover this explicitly.
- [ ] `IdempotencyConfiguration.protectedPaths` includes `/api/v1/payments`. HTTP-layer `Idempotency-Key` semantics work identically to existing endpoints.
- [ ] `openapi.yaml` updated under new `Payments` tag with `PaymentRequest`, `PaymentResponse`, `Rail`, `PaymentState` schemas. `oasdiff` against `main` reports the addition (non-breaking).
- [ ] Acceptance tests under `payments-service/src/test/kotlin/com/malai/payments/acceptance/{happy,sad}/` covering: happy book transfer, ownership-violation 404, non-book request rejection, idempotency replay, response payload shape (`responseEntity().containsProp(json { ... noExtraProps() })`), full-entity DB assertions for the `Payment` + `Transaction` + `LedgerEntry` rows.
- [ ] `malai-payments-client-test-support` provides a `WithPayments` interface in the existing `With*` pattern (request-parameters-as-arguments + response builders).
- [ ] CONTEXT-MAP.md and `payments-service/CONTEXT.md` already exist from the grilling session; nothing new to write here, but the implementation should not contradict them.
- [ ] `mvn install` from the repo root passes.

## Blocked by

None — can start immediately.
