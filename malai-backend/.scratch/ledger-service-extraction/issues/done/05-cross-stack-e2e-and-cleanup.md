# Cross-stack e2e + cleanup

Status: done

## Parent

`.scratch/ledger-service-extraction/PRD.md`

## What to build

Prove the whole extraction end-to-end against a running stack, and remove the now-dead code and schema remnants.

Scope:
- `ledger-service` joins the cross-stack e2e as its **own container** behind the in-JVM WireMock reverse-proxy (matching the established e2e topology); `account-service` and `payments-service` reach it over HTTP.
- Update the existing e2e flows (book transfer, FPS outbound settle/return, SWIFT outbound, FPS/BACS inbound, balance, statement) so each traverses the real ledger HTTP boundary.
- Cleanup: delete the dead ledger/suspense code remaining in `account-service` (entities, repositories, projection logic) and drop the now-redundant `transactions.idempotency_key` column (posting idempotency is the caller-supplied `transaction_id` PK).

## Acceptance criteria

- [x] `ledger-service` runs as its own e2e container; the cross-stack suite is green with payment, book-transfer, and balance flows traversing the ledger boundary. (No dedicated *statement* e2e flow exists — statement is covered at the account-service acceptance level over `WithLedger`; the e2e balance/transactions reads do traverse the boundary.)
- [x] `account-service` contains no ledger entity, repository, or balance-projection code; `payments-service` imports no ledger entity/repository either (verified — completed in issue 04, confirmed here). **Note:** `payments-service` still has the `account-service` *Maven dependency* for account lookups (issue 03's deferred slice — out of scope here and not required by this issue's criteria).
- [x] The `transactions.idempotency_key` column is dropped (V5 + `LedgerTransactionEntity` + `PostingEngine` + test); the seed has no leftover cross-service FKs or retired suspense rows (already correct from issues 02/03).
- [x] `mvn install` (full reactor, all modules) and the full e2e suite (20 tests) pass; the demo is green end-to-end.

## Blocked by

- `.scratch/ledger-service-extraction/issues/03-finish-payments-cutover-swift-inbound.md`
- `.scratch/ledger-service-extraction/issues/04-account-service-read-cutover.md`

## Progress note (2026-06-01) — DONE

**ledger-service as its own e2e container + boundary wiring**
- `docker-compose.e2e.yml`: added the `ledger-service` container (port 8089, shared Postgres, `e2e-tests` profile, no JWT — it has no security, so the `Authorization` header is ignored). Pointed `MAL_LEDGER_BASE_URL` for account-service + payments-service at the in-JVM WireMock reverse-proxy (`/ledger-service`) so each posting/hold/balance call lands on the Kensa diagram and reaches the container.
- `start-e2e-environment.sh`: ledger-service joins the `up --wait` set.
- `MalaiE2ETestContext`: `LEDGER_SERVICE_URL` + `LEDGER_SERVICE_PROXY_PATH` + `toSystemLookup` (→ `LedgerService`) + the `/ledger-service/.*` reverse-proxy stub.
- **Latent boot-blocker fixed:** account-service + payments-service mint a Cognito *service token* for their ledger calls (added in issues 03/04) but the e2e compose never gave them `MAL_AWS_COGNITO_AUTH_SERVICE_*`, so they'd fail to construct the token client. Added that env to both containers (the token endpoint is already stubbed by the registration fixture; the ledger ignores the token).

**e2e flows onto the ledger boundary**
- `WithFunding`: replaced `theBankOwnedSuspenseAccountsExist()` (seeded retired suspense rows into `public.accounts`) with `theLedgerSystemAccountsAreRegistered()` (re-registers the 3 live SYSTEM accounts — SCHEME_SETTLEMENT + FPS/BACS inbound — into `ledger.ledger_accounts`, which the per-class `cleanTables()` truncates). Dropped the retired FPS/SWIFT-outbound suspense constants.
- `PaymentsTest`: rewrote the outbound assertions for the Hold model — a *returned* FPS payment posts nothing (payer keeps only its funding entry), a *settled* FPS/SWIFT payment debits the payer straight to SCHEME_SETTLEMENT (the retired outbound-suspense entry assertions are gone). Inbound (FPS/BACS) and book-transfer assertions unchanged.

**Cleanup**
- Dropped `transactions.idempotency_key` (V5, `LedgerTransactionEntity`, `PostingEngine`, `LedgerTransactionsControllerTest`) — posting idempotency is the caller-supplied `transaction_id` PK. account-service was already free of dead ledger code (issue 04); the V14 seed already had the retired outbound-suspense rows gone and no cross-service FKs.

**Production bug found via the first red e2e run (and fixed)**
- `AccountsService.createAccount` (the public `POST /api/v1/accounts`, used for *additional* accounts e.g. a savings account) was **not registering the new account in the ledger** — only the onboarding path did. So additional accounts couldn't receive book transfers or have holds placed (the ledger rejects postings to unregistered accounts with `LEDGER_ACCOUNT_NOT_REGISTERED`). Added `registerLedgerAccount`, mirroring `OnboardingAccountsService`. The PRD explicitly requires "createAccount calls registerLedgerAccount." Test updates: account-service `AccountsControllerTest` (3 happy create tests prime the register stub; the primary asserts the outgoing register call); malai-api `AccountsApiTest` + `MalaiApiAcceptanceTest` + `runMalaiApi` + pom (point the ledger client at WireMock, mix in `WithLedger`, prime the stub).

**Verified:** full reactor `mvn install` green (all 39 modules); cross-stack e2e green (20/20, incl. all 8 PaymentsTest flows traversing the real ledger HTTP boundary); module suites ledger-service 25 / account-service 124 / malai-api 22.

**Note for the extraction wrap-up:** the only remaining piece of the PRD is issue 03's deferred slice — drop the `account-service` Maven dependency from `payments-service` by building internal account-lookup endpoints + client. Not part of this issue.
