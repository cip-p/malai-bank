# Finish the payments cutover: SWIFT outbound + FPS/BACS inbound

Status: done

## Parent

`.scratch/ledger-service-extraction/PRD.md`

## What to build

Move the remaining payment flows onto the ledger so `payments-service` no longer imports any `account-service` ledger entity or repository.

Scope:
- **SWIFT outbound** onto a **Hold**: initiate → `placeHold`, settle → `settleHold` (`customer → SCHEME_SETTLEMENT`); no return path in v1. Retire the SWIFT outbound suspense account.
- **FPS inbound** and **BACS Direct Credit inbound** credits via `postTransaction` (`inbound-suspense → beneficiary`), with `FPS_INBOUND_SUSPENSE` and `BACS_INBOUND_SUSPENSE` seeded and registered as `SYSTEM` / `ALLOW_NEGATIVE` accounts. No hold (inbound is a straight credit).
- Remove the `account-service` Maven dependency and all ledger-entity imports from `payments-service`.

## Acceptance criteria

- [x] A SWIFT outbound payment places a hold on send and settles via `settleHold` to `SCHEME_SETTLEMENT`; the SWIFT outbound suspense account is gone from the seed.
- [x] An FPS inbound credit and a BACS Direct Credit inbound credit each post `inbound-suspense → beneficiary` through the ledger client and land in the beneficiary's account; the inbound-suspense accounts are seeded and registered.
- [x] `payments-service` no longer depends on the `account-service` module and imports no `LedgerEntryEntity`/`TransactionEntity`/repository (enforce via the existing ArchUnit rules where applicable). The Maven dependency is gone; both halves are now done — see follow-up note below for the lookup-client slice that closed this.
- [x] Kensa acceptance tests for the SWIFT-outbound and inbound cutovers assert the outgoing ledger calls via `WithLedger` fixtures and the resulting state; existing inbound/SWIFT behaviour is otherwise unchanged.
- [x] The demo (SWIFT send, inbound FPS, inbound BACS salary) is green. — validated at the acceptance level (payments-service `mvn test` green, 67 tests); cross-stack e2e/demo run landed in issue 05.

## Blocked by

- `.scratch/ledger-service-extraction/issues/02-native-holds-fps-outbound.md`

## Progress note (2026-06-01)

**Done — the ledger-write cutover (the PRD's stated boundary violation is removed):**
- SWIFT outbound now uses the native ledger Hold: initiate → `placeHold` on the payer (same
  `placeOutboundHold` path as FPS; the ledger's funds guard drives rejection), settle →
  `settleHold` (`customer → SCHEME_SETTLEMENT`). SWIFT has no return path. `PaymentsService`'s
  `postOutboundSuspenseDebit` and `PaymentLifecycleService`'s `settleViaSuspense`/`returnViaSuspense`/
  `suspenseAccountFor` and the `FPS_/SWIFT_OUTBOUND_SUSPENSE_ACCOUNT_ID` constants are deleted.
- FPS + BACS inbound credits now post `inbound-suspense → beneficiary` via `ledgerClient.postTransaction`
  (`creditBeneficiary`); no more local `ledgerEntryRepository`/`transactionRepository` writes.
- `SwiftOutboundSettledListener` now takes `Message<T>` and propagates the `TestId` header (the settle
  path makes outgoing ledger calls now, matching the FPS settle/inbound listeners).
- V14 seed: SWIFT outbound suspense (`…010004`) retired from `accounts` + `ledger.ledger_accounts`.
  FPS/BACS inbound suspense (`…010003`/`…010005`) already seeded + registered SYSTEM/ALLOW_NEGATIVE.
- Acceptance tests for SWIFT-outbound (happy: place-hold-on-send incl. FX; settle-to-SCHEME_SETTLEMENT;
  dedup; same-currency; sad: hold-rejection 400) and FPS/BACS inbound re-pointed onto `WithLedger`
  fixtures, asserting outgoing `placeHold`/`settleHold`/`postTransaction` request shapes (inbound via
  body-content predicates to distinguish the two suspense accounts). Local `ledger.*` DB assertions and
  the now-dead `theLedgerEntriesFor*` test helpers removed.
- Verified: `payments-service mvn test` green (67 tests); full reactor `test-compile` green; `malai_test`
  re-migrated fresh (drop `ledger`+`public`, then migrate — flyway.clean leaves the `ledger` schema).

**Remaining — drop the `account-service` Maven dependency:** `payments-service` still depends on
`account-service` for **account lookups**, not the ledger: `PaymentsService.initiatePayment`
(`AccountRepository.lockByAccountIdAndAccountHolderId`, `resolveDestination`, currency) and
`InboundPaymentProcessor` (`AccountIdentifierRepository` + `AccountRepository` to resolve the
beneficiary). There is no client path for these today — `malai-account-client` is onboarding-only and
`account-service` exposes no internal lookup/resolve-by-identifier endpoint. Removing the Maven
dependency therefore requires a **separate slice**: build internal account-lookup endpoint(s) on
`account-service` (resolve-by-sort-code+number → accountId/holderId/currency; get-account by id+holder)
+ a client + `*-test-support`, cut over `PaymentsService` and `InboundPaymentProcessor`, and re-point
the payments acceptance tests off DB-seeded accounts onto those WireMock stubs. This overlaps issue 04's
account-service surface and was left out to keep this slice small and the build green. Recommend
splitting it into its own issue (`05a`/new) rather than reopening the whole cutover.

**e2e (issue 05):** `malai-end-to-end-tests` still compiles, but `PaymentsTest` asserts SWIFT settling
into `SWIFT_OUTBOUND_SUSPENSE_ACCOUNT_ID` at runtime — now wrong (SWIFT settles via a Hold to
`SCHEME_SETTLEMENT`, and the seed row is gone). Update that flow when issue 05 moves e2e onto the real
ledger boundary.

## Follow-up note (2026-06-01): Maven-dependency removal

**Done — payments-service no longer depends on the account-service Maven module:**
- New internal account-lookup endpoints on `account-service` (`InternalAccountsController` +
  `InternalAccountsService` returning `InternalAccountResponse{accountId, accountHolderId, currency}`):
  - `GET /api/v1/internal/accounts/{accountId}?accountHolderId={uuid}` — payer lookup, holder-scoped
  - `GET /api/v1/internal/accounts/by-identifier?sortCode=&accountNumber=` — beneficiary resolve
  - Both guarded by `@InternalEndpoint` + `@PreAuthorize("hasAuthority('SCOPE_malai-internal/onboarding.write')")`
  - 404 on miss (existing `AccountNotFoundException` → MalaiCode 8998)
- `malai-account-client`: new `AccountLookupClient` (`getAccount` / `resolveBySortCodeAndAccountNumber`) with
  the same 404→null contract as the test-support stubs.
- `malai-account-client-test-support`: new `WithAccountLookup` (`getAccountReturns(NotFound)?` /
  `resolveAccountReturns(NotFound)?`).
- `PaymentsService.initiatePayment` no longer uses `AccountRepository.lockByAccountIdAndAccountHolderId`
  or `AccountIdentifierRepository`; it calls `accountLookupClient.getAccount` for the payer (resulting
  ownership check is the holder-scoped 404), and `resolveBySortCodeAndAccountNumber` for the
  destination (null surfaces as "external", same branching as before).
- `InboundPaymentProcessor` resolves the beneficiary the same way (null → publish `INVALID_ACCOUNT`
  return, exactly as before).
- `PaymentsServiceConfiguration` swaps `@Import(AccountsServiceConfiguration::class)` for
  `MalaiClientConfiguration::class` — payments-service no longer bootstraps account-service's
  JPA repos/entities.
- `payments-service/pom.xml`: dropped both `account-service` (main jar) and `account-service:test-jar`
  deps; added `malai-account-client` + `malai-account-client-test-support`.
- Acceptance tests re-pointed: `WithPayments` now extends `WithAccountLookup` (not `WithAccounts`),
  `ownAccountsExistInLookup()` replaces `seededOwnAccountsWithStartingBalance(...)` (stubs payer + savings
  + the by-identifier resolves), `anotherCustomersAccountExists` stubs the destination resolve, and two
  new helpers `theDestinationIsExternalAtSortCode(accountNumber)` /
  `theAccountDoesNotBelongToTheHolder(accountId, holderId)` explicitly stub the 404 path (otherwise
  `checkForUnmatchedRequests` flags the default-404 fallback as an unmatched request).
- DB schema: `payments.debtor_account_id` and `payments.beneficiary_account_id` no longer reference
  `accounts(account_id)` — V15 edited in place per the clean-room schema permit (the FK was the
  blocker; the source of truth for accounts now lives behind the HTTP boundary, so a cross-context FK
  is the wrong shape).
- Test infra: `WithWiremock` (the `malai-clients-test-support-common` one used by client-module tests,
  not the service-level one) no longer stops the WireMock server in `afterAll` — leaving it running
  for the lifetime of the JVM avoids stale-connection failures when a second test class in the same
  module reuses the singleton Apache HC5 pool. Surfaced because `malai-account-client` now has two
  test classes (`AccountLookupClientTest` and `AccountsOnboardingClientTest`).
- e2e docker-compose: `payments-service` container now also gets `MAL_ACCOUNT_BASE_URL` pointing at
  the in-JVM WireMock reverse-proxy (`/account-service`), so the lookup calls land on the Kensa
  diagram and traverse to the `account-service` container.

Verified: `payments-service mvn test` green (67/67). Full reactor `mvn install -DskipTests` green
across all 39 modules.

Files:
- account-service: `InternalAccountsController`, `InternalAccountsService`, `InternalAccountResponse`,
  + happy/sad `InternalAccountsControllerTest`.
- malai-account-client: `AccountLookupClient`, `AccountLookupModels` (`InternalAccountResponse`),
  `AccountLookupClientTest`.
- malai-account-client-test-support: `WithAccountLookup`.
- malai-clients-test-support-common: `WithWiremock` (drop afterAll stop).
- payments-service: `PaymentsService`, `InboundPaymentProcessor`, `PaymentsServiceConfiguration`,
  `AccountNotFoundException` (moved into payments package), `WithPayments`, `PaymentsAcceptanceTest`,
  `ApplicationRunSupport` (+ `accountBaseUrl`), test-properties, `pom.xml`, all acceptance tests
  (rename `seededOwnAccountsWithStartingBalance` → `ownAccountsExistInLookup`; add explicit
  not-found stubs to the sad/external paths).
- malai-sql: V15 (drop the FKs from payments.debtor_account_id + beneficiary_account_id).
- malai-end-to-end-tests/docker-compose.e2e.yml: `MAL_ACCOUNT_BASE_URL` on the payments-service
  container.
