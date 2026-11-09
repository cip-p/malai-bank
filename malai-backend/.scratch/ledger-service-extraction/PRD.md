# PRD: Extract the ledger into `ledger-service` — the bank's book of record

Status: ready-for-agent

## Problem Statement

The core banking ledger — `transactions`, `ledger_entries`, and the balance projections over them — lives inside `account-service`, and it is reached the wrong way. `payments-service` does not call `account-service`; it imports `account-service`'s JPA entities (`LedgerEntryEntity`, `TransactionEntity`) and repositories directly and calls `ledgerEntryRepository.save(...)` against them, with a Maven dependency whose own comment admits it is "how `payments-service` reaches the book-of-record." Two services now write the ledger by sharing persistence code and a database, with no boundary between them. Root ADR-0002 took that on knowingly; this is the bill.

For a demo whose pitch is "the architecture beneath this bank is one you'd actually want to run," the heart of the system — the ledger — has no seam. A buyer's architect asking "where would we wire in our own core, or Mambu?" has nowhere to point. The brief is explicit: *"in future MalaiBank will either manage balances using an external ledger, like Mambu, or use its own ledger"* — and today there is no API where that swap could happen, only shared tables.

A second, related gap: "block an amount, settle it afterwards" is a capability a real ledger exposes natively, but malai only expresses it indirectly, as `customer → suspense` double-entry postings orchestrated by `payments-service`. The ledger itself has no notion of a reservation, and "available balance" is recomputed outside it.

## Solution

Extract the ledger into a new internal-only **`ledger-service`** — the bank's **book of record** — exposed over a synchronous HTTP API, and design that API as the **Mambu-swappable seam**. The ledger owns `transactions`, `ledger_entries`, balance projections, **Hold**s, the `ledger_accounts` registry, and the bank's **System account**s; it references customer accounts by **opaque UUID** only (no foreign key, no knowledge of account holders) and lives in a **dedicated `ledger` Postgres schema** so the boundary is physical, not conventional. Recorded in `ledger-service` ADR-0001 and ADR-0002.

- **The ledger is a swappable book-of-record.** Callers reach it only through `malai-ledger-client`; replacing malai's ledger with Mambu later becomes a client reimplementation, not surgery. The ledger knows nothing about rails, payments, customers, or onboarding — it moves money between **Ledger account**s on instruction and reports balances.
- **Opaque account references + a registry.** Every `accountId` is an opaque UUID. A **Ledger account** must be *registered* before anything can post to it — `account-service` calls `registerLedgerAccount(id, currency)` when it creates an **Account**; **System account**s are seeded. The registry row carries `currency`, `kind` (`CUSTOMER`|`SYSTEM`), and `guardPolicy` (`NO_OVERDRAFT`|`ALLOW_NEGATIVE`). Posting to an unregistered account is a `4xx`, not silent money creation.
- **The funds guard is the ledger's invariant, on Available balance.** A **Posting** or **Hold** that would take a guarded account's **Available balance** below its floor is rejected atomically inside the ledger (account locked, available checked, applied-or-rejected in one transaction). Callers stop pre-checking and handle a `LEDGER_INSUFFICIENT_FUNDS` rejection — exactly how Mambu rejects a breaching posting.
- **Consistency is an idempotent-posting saga, not a distributed transaction.** The shared DB transaction that made the payment row and its ledger entries atomic is gone the moment the ledger sits behind HTTP. Instead, every **Posting**/**Hold** is idempotent on a caller-supplied id; replaying it is a no-op returning the original result. The `payments` row stays the lifecycle authority (root ADR-0002) and is the saga coordinator that drives retries; the ledger is the money authority; the two reconcile by the shared id.
- **In-flight money is a native Hold.** A **Hold** reserves an amount on a **Ledger account**, reducing its **Available balance** without moving its **Ledger balance**. Lifecycle `PLACED → SETTLED | RELEASED` (no expiry). **Settle** is atomic post-and-release: in one ledger transaction the hold is marked `SETTLED` and a balanced **Posting** debiting the account is recorded, so **Available balance** never blips. **Release** posts nothing. This replaces the **outbound** suspense accounts; on settle an outbound payment posts `customer → SCHEME_SETTLEMENT` directly, and a `RETURNED` outbound payment posts **zero ledger entries**.
- **Three balances, ledger-owned.** **Ledger balance** = `SUM(entries)`; **Held amount** = `SUM(active holds)`; **Available balance** = ledger − held. A behaviour change falls out: an in-flight outbound payment no longer reduces the **Ledger balance** until it settles — only **Available** drops at `PENDING`. The webapp must lead with **Available**.
- **Internal-only, synchronous, no events.** The API is reached over synchronous HTTP via a `malai-ledger-client` / `malai-ledger-client-test-support` pair, following the `malai-account-client` precedent. It is **absent from the hand-authored `openapi.yaml`** (the webapp contract). **No SQS events** — nothing consumes them today; posting is request/response and the saga is driven by idempotent retries.
- **`account-service` keeps the public face.** Its internet-facing balance/transactions/statement controllers stay (URLs unchanged) but become thin composers over the ledger client; the running-balance window query and statement bounds move into the ledger. `BalanceResponse` in `openapi.yaml` gains the `available`/`held` split — the only webapp-visible change.

A buyer reading the architecture now sees the honest picture: a core ledger with its own schema and its own API, holds and settlement as first-class ledger operations, balances computed where the money lives, and a single seam — the ledger HTTP contract — where a real core or Mambu would slot in.

## User Stories

### The book of record & its boundary

1. As a bank technologist evaluating the demo, I want the ledger to be its own service with its own API, so that I can see exactly where I'd wire in my own core banking system or Mambu.
2. As a bank technologist, I want the ledger to reference accounts by opaque id with no foreign key back to the customer system, so that the book of record is genuinely swappable and not entangled with account ownership.
3. As a bank technologist, I want the ledger's tables in their own database schema, so that the boundary is real at the storage layer and not one `ALTER` away from a cross-service join.
4. As a backend engineer, I want `payments-service` to stop importing `account-service`'s ledger entities and call a ledger client instead, so that the long-standing boundary violation is removed and the two services no longer share persistence code.
5. As a backend engineer, I want a single client (`malai-ledger-client`) that every caller uses to reach the ledger, so that the swap to a different ledger is one module's reimplementation.

### Ledger accounts & the registry

6. As a backend engineer, I want a customer **Account** registered with the ledger when it is created, so that the ledger knows the account's currency and guard policy before any money posts to it.
7. As a backend engineer, I want posting to an unregistered account to be rejected with a `4xx`, so that a typo'd or unknown account id can never create money in limbo.
8. As a backend engineer, I want the bank's **System account**s (inbound suspense, scheme-settlement) seeded as `SYSTEM` ledger accounts allowed to go negative, so that they can represent the bank's position without tripping the funds guard.
9. As a backend engineer, I want each ledger account to carry a guard policy (`NO_OVERDRAFT` for customers, `ALLOW_NEGATIVE` for system accounts), so that the funds guard is data-driven rather than special-cased in code.

### Posting money

10. As a backend engineer, I want to post a balanced transaction (entries summing to zero) through one ledger call, so that book transfers, settlements, and inbound credits all go through the same audited primitive.
11. As a backend engineer, I want every posting to be idempotent on a caller-supplied transaction id, so that an HTTP retry after a timeout cannot double-move money.
12. As a backend engineer, I want a posting that would breach a guarded account's **Available balance** rejected atomically with `LEDGER_INSUFFICIENT_FUNDS`, so that I never have to pre-check the balance in a separate, racy call.
13. As a backend engineer, I want the sum-to-zero invariant enforced by the database, so that no caller — now or after a Mambu swap — can record an unbalanced transaction.
14. As a backend engineer, I want entry metadata (`description`, `counterpartyId`, `merchantName`, `category`) carried as opaque passthrough, so that the webapp's transaction list keeps its enrichment without the ledger having to interpret it.

### Holds (block & settle)

15. As a customer, I want money I've sent but that hasn't settled to be unavailable to spend again, so that I can't accidentally overspend funds already committed.
16. As a customer, I want my account "Balance" to stay put until a payment actually settles while my "Available" drops immediately, so that the figures reflect what's truly mine versus what's committed.
17. As a backend engineer, I want to place a hold that reduces **Available balance** without moving **Ledger balance**, so that an in-flight outbound payment reserves the payer's funds the way a real ledger does.
18. As a backend engineer, I want to settle a hold in one atomic post-and-release, so that the reservation becomes a real debit with no window where the money is both held and posted (or neither).
19. As a backend engineer, I want to release a hold without posting anything, so that a returned payment simply frees the reservation and leaves no phantom ledger entries.
20. As a backend engineer, I want placing a hold to be funds-guarded on **Available balance**, so that two concurrent payments can't both reserve the same money.
21. As a backend engineer, I want holds and settles idempotent on the hold id, so that retried lifecycle transitions are safe.
22. As a demonstrator, I want "our ledger supports holds / blocks like a real bank" to be a thing I can show, so that the ledger reads as production-grade core banking.

### Balances & reads

23. As a customer, I want to see my balance on my accounts, so that I can make better-informed spending decisions.
24. As a backend engineer, I want the ledger to compute **Ledger balance**, **Held amount**, and **Available balance** in one call, so that callers never re-derive balances from payment rows.
25. As a customer, I want my recent transactions with a running balance, so that I can follow how each posting changed my account.
26. As a customer, I want a monthly statement with opening and closing balances, so that I can reconcile a period.
27. As a backend engineer, I want the running-balance window query and statement bounds to live in the ledger, so that the projection sits where the entries are.
28. As a backend engineer, I want confirmation-of-funds derived from `getBalances` (`target ≤ available`) rather than a dedicated endpoint, so that the surface stays minimal.
29. As a customer, I want a held-but-unsettled payment to *not* clutter my posted transactions or statement, so that my statement shows only money that actually moved.

### The payment lifecycle over the new boundary

30. As a customer, I want a same-bank own→own transfer to complete instantly, so that moving money between my accounts is immediate (a single posting, no hold).
31. As a customer, I want an FPS payment to reserve my funds when I send it and debit me when it settles, so that the money leaves my balance at the right moment.
32. As a customer, I want a returned FPS payment to restore my available funds with no trace of a debit, so that a failed payment looks like it never moved money.
33. As a customer, I want an inbound payment credited to my account, so that I can receive money from other banks and Malai customers.
34. As a backend engineer, I want `payments-service` to drive holds and postings through the ledger client while the `payments` row remains the lifecycle authority, so that the state machine coordinates the saga and reconciles by id.
35. As a backend engineer, I want a stuck `PENDING` payment re-driven by re-issuing the same idempotent ledger calls, so that a mid-flight failure recovers without double-posting.

### Account service as a thin composer

36. As a webapp developer, I want the balance/transactions/statement endpoints to keep their URLs and shapes (plus a new `available`/`held` split on balance), so that the cutover doesn't break existing screens beyond surfacing Available.
37. As a backend engineer, I want `account-service` to compose ledger reads with account metadata rather than owning the ledger, so that its responsibility narrows to accounts, identifiers, directory, and CoP.

### Schema, seed & topology

38. As a backend engineer, I want the ledger tables in a `ledger` schema and the cross-service FKs dropped, so that the ledger can't be FK-coupled to `accounts`.
39. As a backend engineer, I want the existing Flyway scripts edited in place (no new migration files), so that the clean-room schema stays coherent and rebuildable.
40. As a demonstrator, I want the seed to register every seeded customer and system account in the ledger and drop the outbound suspense accounts, so that the demo dataset matches the new model.
41. As an operator, I want `ledger-service` bundled into `malai-api` like every other service, so that the deployment topology stays flat with no new ECS service.
42. As a demonstrator, I want `ledger-service` running as its own container in cross-stack e2e, so that the demo exercises the real ledger HTTP boundary end-to-end.

### Migration safety

43. As a demonstrator, I want the demo green at every merge, so that I can show the product at any point during the extraction.
44. As a backend engineer, I want book transfer cut over first (one posting, no hold), so that the client boundary is proven on the simplest flow before holds are introduced.
45. As a backend engineer, I want FPS moved onto holds next, then the account-service read cutover, then e2e, so that the blast radius per slice stays small.

### Testing

46. As a test author, I want every ledger operation asserted at its HTTP boundary and by full-entity database assertions, so that "test all outgoing and incoming messages" holds for the ledger's API.
47. As a test author, I want the funds-guard rejection and the idempotent-replay no-op as first-class test cases, so that the two hardest invariants are pinned.
48. As a test author, I want each caller's outgoing ledger calls asserted via WireMock fixtures, so that request shape and response handling are verified on both sides of the boundary.

## Implementation Decisions

**`ledger-service` (new `malai-services` module, bundled into `malai-api`).** Owns the `ledger` schema. Internal-only HTTP API; absent from `openapi.yaml`. Composed of three deep modules plus thin adapters:

- **Posting engine (deep module).** Interface: `postTransaction(transactionId, entries[], reference, postedAt, traceId)`. Locks the guarded accounts, checks **Available balance**, enforces sum-to-zero, persists the `transactions` row + `ledger_entries`, all in one DB transaction. Idempotent on `transactionId` (replay returns the original result). Rejects breaches with `LEDGER_INSUFFICIENT_FUNDS` and unregistered accounts with a `4xx`. This is the heart; everything else is built on it.
- **Hold manager (deep module).** Interface: `placeHold(holdId, accountId, amount)`, `settleHold(holdId, posting)`, `releaseHold(holdId)`. Owns the `PLACED → SETTLED | RELEASED` lifecycle and the available-balance arithmetic. `settleHold` marks the hold settled and records the balanced debit posting in one transaction (atomic post-and-release). A hold carries only `(accountId, amount)`; the destination is supplied at settle. Full-amount settle only. Funds-guarded on place. Idempotent on `holdId`.
- **Balance & entries projection (deep module).** Interface: `getBalances(accountId) → { ledgerBalance, heldAmount, availableBalance }`, `listEntries(accountId, [periodStart, periodEnd], limit) → entries with running balance`. The running-balance window function and statement opening/closing bounds move here wholesale from `account-service`. No mutable balance column — every figure is projected on read.
- **Ledger-account registry (thin).** Interface: `registerLedgerAccount(accountId, currency, kind, guardPolicy)` and an internal lookup used by the posting engine and hold manager for guard policy + currency.
- **Controllers (thin adapters)** over each operation; no logic beyond request/response mapping.

**`malai-ledger-client` + `malai-ledger-client-test-support` (new client pair, under `malai-clients`).** The production client extends `AbstractMalaiClient`; the test-support module provides a `WithLedger` interface of WireMock stub methods (request params as direct arguments) and response builders (`BalancesResponseBuilder`, `EntriesResponseBuilder`, …) implementing `TestBuilder`. No paired entry in `openapi.yaml`.

**`payments-service` (modified).** `PaymentsService` and `PaymentLifecycleService` stop importing `LedgerEntryEntity`/`TransactionEntity`/repositories and the `account-service` Maven dependency. Outbound initiate → `placeHold` on the payer (after the funds guard, which now lives in the ledger). Settle → `settleHold` (`customer → SCHEME_SETTLEMENT`), plus a `postTransaction` `SCHEME_SETTLEMENT → beneficiary` credit when the beneficiary is on-us. Return → `releaseHold` (no postings). Book transfer → a single `postTransaction` (payer → payee), born `SETTLED`, no hold. Inbound credit → `postTransaction` (inbound-suspense → beneficiary). The `payments` row remains the lifecycle authority and saga coordinator. Outbound suspense topology is deleted.

**`account-service` (modified).** Loses `LedgerEntryEntity`/`TransactionEntity`, their repositories, and the balance/statement query logic. `createAccount` calls `registerLedgerAccount`. The balance/transactions/statement controllers stay (internet-facing, openapi-contracted) and become thin composers: call `getBalances`/`listEntries`, wrap with account metadata (accountUid, currency, name). `getConfirmationOfFunds` derives from `getBalances`. Its responsibility narrows to accounts, identifiers, directory, and CoP.

**Schema & migrations (`malai-sql`, existing scripts edited in place — no new files).** Move `transactions`, `ledger_entries`, plus new `holds` and `ledger_accounts` into a `ledger` schema; keep `accounts`/`account_identifiers` in `public`. Drop the cross-service FKs on `ledger_entries` (→ `accounts`); keep `transaction_id → transactions` and the V5 sum-to-zero deferred trigger. `ledger_accounts`: `(account_id, currency, kind, guard_policy)`. `holds`: `(hold_id, account_id, amount_minor_units, status, placed_at, resolved_at)`. Rewrite the seed: drop outbound suspense rows; keep inbound-suspense + `SCHEME_SETTLEMENT` as `SYSTEM` accounts; register every seeded customer + system account in `ledger_accounts`. The now-redundant `transactions.idempotency_key` column is noted for cleanup, not a blocker.

**Balance contract change.** `BalanceResponse` in `openapi.yaml` gains `availableBalanceMinorUnits` and `heldAmountMinorUnits` alongside the existing balance (now explicitly **Ledger balance**). The webapp regenerates its client and leads with **Available**. URLs unchanged.

**`malai-api` & root `pom.xml`.** Add `ledger-service` as a dependency of `malai-api` and import its `@Configuration`. Register `ledger-service`, `malai-ledger-client`, `malai-ledger-client-test-support` in the root `pom.xml` `<modules>` and `<dependencyManagement>`.

**Seeders.** `malai-seeder` and `malai-registration-seeder` register ledger accounts and write ledger data into the `ledger` schema (directly, as dev-only tooling) rather than the old `account-service` repositories.

**No events.** `ledger-service` produces and consumes no SQS. The SQS plumbing stays exclusively a `payments-service`↔gateway concern.

**Slice order.** (1) Stand up `ledger-service` + client pair + schema, cut **book transfer** over first (one `postTransaction`, no hold) to prove the boundary; (2) **holds for FPS** (place/settle/release; retire outbound suspense; rewire settle/return; inbound credit via `postTransaction`); (3) **account-service read cutover** (balances/transactions/statement composed over the ledger; `BalanceResponse` split; register on create); (4) **e2e** (ledger as its own container; flows traverse the boundary). `/to-issues` can break these into tickets.

## Testing Decisions

**What makes a good test here.** Tests assert external behaviour at the ledger's HTTP boundary and the resulting persisted state — never internals. For a write, the observable facts are the HTTP response *and* the rows the operation produced; for a read, the response computed from seeded entries/holds. A test feeds one operation and asserts the response plus the full entity state; it never invokes a service method or repository directly. The funds-guard rejection and the idempotent-replay no-op are explicit behavioural cases, not afterthoughts.

**Modules tested (all four confirmed in scope):**
- **`ledger-service` acceptance tests** (Kensa BDD) — one per operation: `postTransaction` (balanced happy path, sum-to-zero rejection, guard rejection, idempotent replay, unregistered-account `4xx`), `placeHold`/`settleHold`/`releaseHold` (lifecycle + available-balance effects + atomic settle + idempotent replay), `getBalances` (ledger/held/available across postings and active holds), `listEntries` (recent + statement period, running balance), `registerLedgerAccount`. Response payloads asserted with `responseEntity().containsProp(json { … noExtraProps() })`; state asserted with full-entity database assertions (`theLedgerAccountInTheDatabaseIs(...)`, `theHoldInTheDatabaseIs(...)`, entry-set assertions) so any new field forces a test update.
- **`malai-ledger-client` tests** — request construction (method, URL, headers, body) and response deserialisation for every operation, modelled on `AccountsOnboardingClientTest`, using `something` for dynamic values.
- **`payments-service` + `account-service` cutover tests** — existing acceptance tests re-pointed through the new `WithLedger` WireMock fixtures, asserting each service's *outgoing* ledger request shape and its handling of the response (incl. the `LEDGER_INSUFFICIENT_FUNDS` rejection path). These replace the old in-process ledger-repository assertions.
- **Cross-stack e2e** (`malai-end-to-end-tests`) — the existing payment, book-transfer, balance, and statement flows updated so each traverses the real `ledger-service` HTTP boundary, with `ledger-service` running as its own container behind the in-JVM WireMock reverse-proxy.

**Prior art.** Model ledger acceptance tests on `PaymentsAcceptanceTest`/`WithPayments` and `AccountsService` tests (Kensa given/when/then, full-entity DB assertions, `noExtraProps()`). Model the client tests on `AccountsOnboardingClientTest`. Model `WithLedger` fixtures + builders on `malai-account-client-test-support` (`WithAccounts` + response builders implementing `TestBuilder`). The repository tests for any custom `*Repository` functions (balance projections, running-balance window query, hold queries) follow the existing repository-test requirement.

**Not tested in isolation.** The three deep modules are exercised through the service's HTTP acceptance tests rather than via direct unit tests on their Kotlin interfaces — consistent with the project's behaviour-first posture and how `payments-service` covers its lifecycle service. A wrong projection or guard breaks the acceptance assertion.

## Out of Scope

- **Native hold features beyond v1** — no expiry/auto-release sweeper, no partial capture; settle is full-amount only.
- **A confirmation-of-funds endpoint** — derived from `getBalances`.
- **SQS events from the ledger** — none; no `LedgerPosted` broadcast, no reactive balance push in v1.
- **A separate physical database or two datasources** — one Postgres instance, isolation via the `ledger` schema only.
- **Moving entry enrichment out of the ledger** — `merchantName`/`category` stay as opaque passthrough on the entry; a separate enrichment store is not built.
- **New migration files** — existing Flyway scripts are edited in place under clean-room latitude.
- **Changes to rails, gateways, schemes, or the SQS catalog** — `payments-service`↔gateway transport is untouched; this changes only how `payments-service` records money.
- **Webapp work beyond regenerating the client and leading with Available** — the webapp's own changes are tracked in its repo.
- **`malai-infra` Terraform** — `ledger-service` is bundled into `malai-api`; no new ECS service. Recorded for the infra repo only if datasource config changes.
- **Mambu integration itself** — this builds the seam, not the Mambu client.

## Further Notes

- This is a behaviour-preserving extraction on the *read* side and a deliberate behaviour *change* on the in-flight side: under holds, an in-flight outbound payment no longer reduces **Ledger balance** until settlement, and a returned payment posts nothing. The webapp must surface **Available balance** prominently or customers will see committed money as spendable. Captured in `ledger-service` ADR-0002 and reconciled in the `payments-service` glossary.
- The atomicity model is the crux. There is no distributed transaction; correctness rests on idempotent operations keyed by caller-supplied ids plus the `payments` state machine as coordinator. This is precisely the contract a real external ledger (Mambu) would impose, which is why solving it now is the point rather than a cost.
- The funds guard moving *into* the ledger is what makes the boundary safe: a check-then-post split across HTTP would be racy, so the ledger locks, checks **Available balance**, and applies-or-rejects in one transaction. Callers handle the rejection; they never pre-check.
- Supersedes the outbound suspense-account mechanism in `payments-service` ADR-0001/0002; inbound suspense and `SCHEME_SETTLEMENT` survive as `ledger-service` **System account**s.
- Glossary and map are already updated: `ledger-service/CONTEXT.md` defines **Ledger**, **Ledger account**, **Posting**, **Transaction**, **Ledger entry**, **Hold**, **System account**, **Ledger/Held/Available balance**; `CONTEXT-MAP.md` registers the new context and moves the ledger terms out of `account-service`; the `payments-service` glossary's `Available`/`Settled balance` and `Suspense account` terms are reconciled.
- Suggested slice order for `/to-issues`: book transfer first, then FPS holds, then the account-service read cutover, then e2e — each a vertical slice that keeps the demo green.
