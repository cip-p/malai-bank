# 08 — Credit card application: instant approval, CREDIT_CARD account, floor at −limit

Status: ready-for-agent

## Parent

`.scratch/cards/PRD.md`

## What to build

The ten-second credit card: a customer submits a short **Credit card application** and is instantly approved — atomically creating a **Credit card account** (`AccountType.CREDIT_CARD` in account-service, with ordinary sort-code/account-number identifiers), registering its ledger account with a **floor at −£2,500** (the fixed demo **Credit limit**), and issuing its `CREDIT` variant **Card**. The application is theatre: no underwriting, always approved, never issued at registration.

From then on the existing machinery does the work: authorisations on the credit card ride the issue-04 decision path with the hold landing on the credit account; the issue-02 floor declines anything that would breach the limit (mapped to the over-limit decline reason); spending drives the ledger balance negative. A **credit summary** surface — owed (negated ledger balance), available credit (limit + available balance), and the limit — feeds the webapp's account header and, later, the Pay card presets. The credit account appears in the customer's account list; it is excluded from Confirmation of Payee eligibility.

Domain language: **Credit card account**, **Credit limit**, **Credit card application** in `card-service` CONTEXT.md.

## Acceptance criteria

- [ ] OpenAPI edited first: the application path and the credit summary path, additive, `oasdiff` green
- [ ] `AccountType` gains `CREDIT_CARD`; the application creates account + identifiers + ledger registration (floor = −250000 minor units, asserted on the outgoing ledger request) + `CREDIT` card, atomically — a failure in any step leaves no partial estate
- [ ] The credit account appears in the customer's account list with its type; identifiers are generated like any account's
- [ ] `CREDIT_CARD` is excluded from Confirmation of Payee eligibility (acceptance test proves a CoP lookup against it does not confirm)
- [ ] Authorisation on the credit card up to available credit approves (hold on the credit account, asserted); an authorisation breaching the floor declines with the over-limit reason via the funds-guard mapping — proven end to end through the decision surface
- [ ] Credit summary returns owed, available credit, and limit with the formulas above; asserted for fresh (nothing owed), mid-spend (active holds count against available credit), and settled-spend states
- [ ] One application per holder in flight is not a concern, but a holder who already has an active credit card account is politely rejected (one credit card per holder in v1)
- [ ] Application endpoint: holder-scoped, `Idempotency-Key` honoured (replay returns the original account + card, no duplicates), audit event emitted
- [ ] Client methods added where composition requires them, with WireMock-backed contract tests; any custom repository functions tested
- [ ] Kensa acceptance happy/sad suites; strict Kacey with `noExtraProps()`; full-entity database assertions across both services

## Blocked by

- `.scratch/cards/issues/02-ledger-per-account-floor-guard.md`
- `.scratch/cards/issues/04-first-authorised-purchase.md`

Status: done (2026-07-12)

## Completion note

Both the **Credit card application** and the **Credit summary** are served by
`account-service` (it already owns atomic account + identifiers + floored-ledger
registration + card-client issuance and the ledger-balance reads); `card-service`
needed **zero** changes — the `CREDIT` variant already existed and `CardIssuer.issue`
already takes the variant. The application is a near-verbatim extension of
`AccountsService.createAccount` with `guardPolicy=FLOORED / floorMinorUnits=-250000`,
`variant=CREDIT`, `accountType=CREDIT_CARD`, and a one-per-holder guard.

Key decisions:
- **Idempotency critique surfaced and mitigated in-slice (per CLAUDE.md / the issue-07
  note).** The shared interceptor keys on `(service, key)` and hashes
  `method + URI + query + body` — **not** the principal. A collection-POST
  `POST /api/v1/credit-card-applications` with an identical (empty) body across holders
  would let two holders sharing an `Idempotency-Key` replay each other's response — a
  cross-tenant leak, exactly what issue-07 warned about for path-id-less endpoints. Fix
  without widening the shared package: embed `{accountHolderId}` in the path
  (`POST /api/v1/credit-card-applications/{accountHolderId}`), mirroring the transfers
  `{accountId}` precedent — the holder id is part of the request hash, so a key reused
  across holders is a **422 payload-mismatch, never a replay** (proven by the
  `anIdempotencyKeyReusedAcrossHoldersIsAPayloadMismatchNotACrossTenantReplay` test).
  The controller also rejects a path holder ≠ principal with 404. The package-level
  per-principal-scoping fix remains open.
- Replay (same key, same holder) returns the original account + card with **exactly one**
  card-issue call (interceptor replay, proven). One-per-holder: a fresh key when a
  `CREDIT_CARD` account already exists → 409 `CREDIT_CARD_ACCOUNT_ALREADY_EXISTS(6992)`.
- **Atomicity**: account + identifiers + audit commit/roll-back together in the
  `@Transactional` application method (a card-issue 500 leaves **no** account or
  identifiers — proven). Same inline, non-compensated posture as the existing
  create-account flow for the remote ledger register (an orphan ledger account on a
  later-leg failure is accepted at demo scale, matching issue 03).
- **Credit summary** (`GET /api/v1/accounts/{accountUid}/credit-summary`, account-service):
  `owed = -ledgerBalance`, `availableCredit = creditLimit + availableBalance` (so active
  **Holds** reduce it immediately), `creditLimit = 250000` (fixed). Asserted fresh
  (0 owed / full limit), mid-spend (hold reduces available credit, owed still 0), and
  settled-spend (owed > 0, reduced available credit). 404 for a non-`CREDIT_CARD` or
  foreign account.
- **CoP exclusion**: `CREDIT_CARD` is simply left out of
  `ConfirmationOfPayeeService.ELIGIBLE_ACCOUNT_TYPES`, so a lookup federates to the stub
  and returns `NO_MATCH` (proven).
- Contract-first: two additive paths + `CreditSummaryResponse` / `CreditCardApplicationResponse`
  schemas (oasdiff: 0 ERR / 0 WARN / 2 INFO endpoint-added). `account_type` is `VARCHAR(30)`
  with no CHECK, so the new enum value needs **no** migration.

Files:
- OpenAPI: `POST /api/v1/credit-card-applications/{accountHolderId}`,
  `GET /api/v1/accounts/{accountUid}/credit-summary`, `CreditSummaryResponse`,
  `CreditCardApplicationResponse`.
- malai-commons: `MalaiCode.CREDIT_CARD_ACCOUNT_ALREADY_EXISTS(6992)`.
- account-service: `AccountType.CREDIT_CARD`; `AccountsService.applyForCreditCard` +
  `getCreditSummary` + `toIssuedCardResponse` + constants; `CreditCardApplicationController`;
  `AccountsController.getCreditSummary`; `CreditCardApplicationResponse` /
  `IssuedCardResponse` / `CreditSummaryResponse` models; `accountServiceIdempotencyRoutes`
  + `accountServiceSourceRoutes` + `malai.secured.paths` gain the new prefix.
- malai-api: `malai.secured.paths` gains the new prefix.
- malai-card-client-test-support: `WithCards.issueCardReturnsServerError`.
- card-service: `CONTEXT.md` gains a **Credit summary** term + a cross-context note that the
  application/summary live in account-service.
- tests: happy `CreditCardApplicationControllerTest` (6 — application full-entity + ledger
  FLOORED/-250000 + card CREDIT + audit + response, replay-no-duplicate, appears-in-list,
  credit-summary fresh/holds/settled); sad `CreditCardApplicationControllerTest` (7 —
  one-per-holder 409, atomic rollback, foreign-path 404, missing-key 400, cross-holder 422,
  credit-summary non-credit 404, credit-summary foreign 404); CoP happy gains the
  `CREDIT_CARD → NO_MATCH` test.

Verification (serial mvn, Postgres + Localstack + WireMock up; npm test/typecheck N/A in this
Maven/Kotlin repo): full reactor compiles + installs clean (`-DskipTests`, EXIT 0). Tests green
— account-service 161/0/0 (147 → 161; +14 new), malai-api 21/0/0 (aggregator boots the new
controller + idempotency route + secured paths in one JVM; `MalaiApiArchitectureTest` and
`OpenApiSpecTest` green). oasdiff breaking `--fail-on ERR` EXIT 0 (2 INFO additive).

Notes for next iteration:
- Issue 09 (statements + Pay card repayment) unblocked: the `CREDIT_CARD` account is
  ledger-backed and addressable (ordinary identifiers), so repayment is an existing book
  transfer; the credit summary is the natural home for issue 09's current-cycle statement
  figures. If issue 09 puts the **StatementCalculator** in `card-service` (per the PRD),
  account-service composes the figures via the card client (as it already does for feed
  composition) — no regret from the summary living in account-service.
- The credit summary's `owed = -ledgerBalance` goes negative on overpayment; issue 09 owns
  the overpay → positive-credit-balance / zero-minimum behaviour.
- The per-principal idempotency-scoping critique remains open; this slice leans on the
  path-embedded holder id, so any future path-id-less write endpoint must not rely on that.
