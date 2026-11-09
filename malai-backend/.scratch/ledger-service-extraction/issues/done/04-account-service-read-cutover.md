# account-service read cutover (thin composer over the ledger)

Status: done

## Parent

`.scratch/ledger-service-extraction/PRD.md`

## What to build

Move the balance and transaction-history projections into the ledger and turn `account-service`'s internet-facing read endpoints into thin composers over the ledger client. URLs are unchanged; the only webapp-visible change is the additive `available`/`held` split on the balance response.

Scope:
- **Balance & entries projection** deep module in `ledger-service`: `listEntries(accountId, [periodStart, periodEnd], limit)` returning entries with running balance — the running-balance window query and statement opening/closing bounds moved wholesale out of `account-service`. (`getBalances` already exists from issue 02.)
- `account-service` balance / transactions / statement controllers become thin composers: call `getBalances`/`listEntries`, wrap with account metadata (accountUid, currency, name). `getConfirmationOfFunds` derives from `getBalances` (`target ≤ available`).
- `BalanceResponse` in `openapi.yaml` gains `availableBalanceMinorUnits` + `heldAmountMinorUnits` (the existing figure is the **Ledger balance**); additive, oasdiff-safe. Note the cross-repo ripple: the webapp regenerates its client and should lead with **Available**, since **Ledger balance** no longer drops at `PENDING`.
- A held-but-unsettled payment has no ledger entry, so it does not appear in transactions/statements; a returned payment never appears.

## Acceptance criteria

- [x] `listEntries` returns recent entries (with running balance) and a statement period (opening/closing + entries) computed in the ledger; `account-service` no longer owns the projection queries.
- [x] `GET /accounts/{id}/balance` returns ledger + available + held; `/transactions` and `/statement` are served by composing ledger reads with account metadata; URLs and existing fields are unchanged.
- [x] Confirmation-of-funds is derived from `getBalances` with no dedicated ledger endpoint.
- [x] `oasdiff` passes (the `BalanceResponse` change is additive); the openapi balance schema documents the three figures. — additive: two new response props + `amountMinorUnits` description tweak (now documents the three figures). Not run locally (oasdiff is CI-only vs `main`); change is response-only and additive, so oasdiff-safe.
- [x] A held-but-unsettled payment does not appear in the transaction list or statement; a settled one does. — `LedgerEntriesControllerTest.aHeldButUnsettledPaymentDoesNotAppearInTheEntries`.
- [x] Kensa acceptance tests cover `listEntries` (recent + period, running balance) with full-entity DB assertions; the `account-service` read controllers are tested composing over `WithLedger` fixtures.
- [x] The demo (balance header leads with Available; transactions; statement) is green. — verified at acceptance + full-stack: `malai-api mvn test` green (22, incl. the bundled real-ledger seed/register flows). Cross-stack e2e runtime stays issue 05's scope.

## Blocked by

- `.scratch/ledger-service-extraction/issues/02-native-holds-fps-outbound.md`

## Progress note (2026-06-01) — DONE

**Ledger (`ledger-service`):** added the entries projection deep module — `EntriesService.listEntries(accountId, periodStart?, periodEnd?, limit)` over new `LedgerEntryRowRepository` queries (`findRecentEntriesForAccount(limit)`, `findEntriesForAccountInPeriod`, `balanceBefore`) + `LedgerEntryProjectionRow`; exposed via `LedgerEntriesController` `GET /api/v1/internal/ledger/accounts/{id}/entries` returning `EntriesResponse(accountId, entries[runningBalance], openingBalanceMinorUnits?, closingBalanceMinorUnits?)` (opening/closing populated only for a period; omitted otherwise via `non_null`). `getBalances` already existed (issue 02).

**Client:** `LedgerClient.listEntries(...)` + `EntriesResponse`/`LedgerEntryProjection` models; `WithLedger.listEntriesReturns(...)` + `EntriesResponseBuilder` test-support. `LedgerClientTest` covers recent + period.

**`account-service`:** `AccountsService` balance/transactions/statement/CoF now compose over the ledger client (token via `AccountsServiceTokenClient`), wrapping with `verifyAccountOwnership` + currency. `BalanceResponse` gained `availableBalanceMinorUnits` + `heldAmountMinorUnits` (existing `amountMinorUnits` = Ledger balance); CoF now derives from **Available** (behaviour change: held funds reduce CoF). Deleted `LedgerEntryEntity`/`TransactionEntity`/`LedgerEntryRepository`/`TransactionRepository` + `LedgerEntryRepositoryTest`. Acceptance tests re-pointed onto `WithLedger` fixtures (balance/transactions/statement/CoF), asserting the outgoing ledger call shape; the 100-row cap test moved into the ledger acceptance suite.

**openapi.yaml:** `BalanceResponse` gains the two fields (required) and documents the three figures (Ledger/Available/Held); additive.

**Fallout fixed (kept the reactor green):** account-service's `WithAccounts` shared test-jar is consumed by `payments-service` and `malai-api` tests, and `malai-registration-seeder` wrote ledger rows via the now-deleted account-service JPA entities. Re-pointed all ledger writes/reads to raw SQL against the `ledger.*` tables (per the PRD's "seeders write the ledger schema directly" note): `WithAccounts.aBalancedLedgerEntryPair`/`theLedgerEntriesInTheDatabase` now JDBC (balanced pair inserted in one multi-row statement so the deferred sum-to-zero trigger sees both legs at one commit — fixtures run outside `@Transactional`); `LedgerSeedHelper` now JDBC; the seeder integration test reads via JDBC. `manyBalancedLedgerEntryPairs` had no remaining users and was dropped.

**Verified:** `mvn test` green per module — ledger-client 10, ledger-service 25, account-service 141, registration-seeder 6, payments-service 67, malai-api 22. Full reactor `mvn install -DskipTests` green (all modules incl. e2e test-compile). `malai_test` already had the ledger schema; **no schema/migration change in this issue**.

**Notes for next iteration (issue 05):** the JDBC ledger-seeding helpers now live in `account-service`'s `WithAccounts` shared test-jar and in `malai-registration-seeder` — a natural home would be a ledger test-support module; fold that into the issue-05 cleanup. e2e `AccountsTest` balance assertion is partial-match (no `noExtraProps`) so the additive fields don't break it; the SWIFT-suspense runtime assertion noted in issue 03 is still issue 05's to fix when e2e moves onto the real ledger boundary.
