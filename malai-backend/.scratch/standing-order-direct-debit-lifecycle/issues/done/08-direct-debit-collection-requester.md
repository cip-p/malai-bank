# 08 — Direct-debit collection requester

Status: done

## Parent

`.scratch/standing-order-direct-debit-lifecycle/PRD.md`

## What to build

The mandate clock. A per-minute, lock-coordinated scheduler in the direct-debit service scans ACTIVE mandates whose next collection date has arrived and asks the stub (via the stub client) to originate each collection, so the money still arrives through the gateway and queue like every other inbound payment — the stub remains the apparent originator while the mandate table stays the source of schedule truth (ADR 0004). Each collection carries the deterministic end-to-end ID derived from mandate ID and collection date; request first, advance second, one-fire resync on the shared anchor arithmetic. Collections are for the mandate's fixed amount. Cancelled mandates are never collected.

## Acceptance criteria

- [x] Scheduled job runs per minute with lock coordination; dueness from the injected time service
- [x] Due ACTIVE mandate → exact outgoing origination request to the stub asserted (WireMock), including creditor details, fixed amount, and the deterministic end-to-end ID; next collection date advanced — full-entity database assertion
- [x] One-fire resync: a mandate overdue by several periods collects exactly once and lands on the next strictly-future occurrence, on the shared anchor arithmetic
- [x] CANCELLED mandates are never scanned; re-fired duplicates dedup harmlessly downstream via the end-to-end ID
- [x] Repository tests for the due-scan query
- [x] Kensa acceptance tests drive the requester under stubbed time
- [ ] Verifiable whole: a seeded mandate made due under stubbed time produces, via the rail from the inbound-rail slice, a settled collection and a feed line

## Progress

2026-07-07 — backend collection-requester slice landed on `main`. The per-minute
ShedLock-coordinated `DirectDebitCollectionRequester.runOnSchedule` → `requestDueCollections`
scans ACTIVE mandates whose `next_collection_at` has arrived and asks the stub (via
`MalaiStubClient.postCollectionBacs`) to originate each collection, then advances the schedule.
The stub stays the apparent originator; the mandate table is the source of schedule truth
(ADR 0004). The collection travels the issue-07 rail (stub → gateway → queue → payments consumer).

Key decisions:
- Occurrence arithmetic reuses the shared `com.malai.commons.schedule.OccurrenceSchedule` +
  `ScheduledPaymentEndToEndId` (built for the standing-order executor, issue 06) — anchor +
  n×frequency with month-end clamp, `nextStrictlyAfter` for the one-fire resync, and a
  deterministic `f(mandateId, occurrenceDate)` end-to-end id so a re-fire after a crash between
  request and advance dedups harmlessly on the queue-message dedup table downstream.
- Request first, advance second: the testable `requestDueCollections` worker is split from a
  `@Transactional` `DirectDebitCollectionRequestService.requestCollection` (per-mandate) so the
  proxy boundary applies and acceptance tests drive the logic deterministically under stubbed
  time without fighting ShedLock's unlock timing (mirrors the standing-order executor split).
  The `@Scheduled` entry carries `@SchedulerLock` (the `everyScheduledMethodHasSchedulerLock`
  arch rule is now non-vacuous, 19 arch tests green); cron is disabled (`-`) in direct-debit +
  malai-api test props so it never auto-fires. `lockAtMostFor=50s`.
- **Debtor resolution via shared SQL, not a client + OAuth.** The requester's only downstream
  call is to the stub, which needs no auth — so rather than pull in `malai-account-client` +
  a Cognito service-token client + cognito blocks (the sandbox route), a new
  `DirectDebitsRepository.findDebtorSnapshot(accountId)` reads the debtor's `(name, sortCode,
  accountNumber)` from `accounts` + `account_identifiers` (SORT_CODE) via the same
  modular-monolith shared-SQL join the DD/standing-order repositories already use for `accounts`.
  The requester sends the stored `bank_identifier`/`account_identifier`, which the inbound rail
  resolves back by the same pair — the round trip closes.
- New due-scan `findDueActiveMandates(asOf)` (global, ACTIVE + non-null + `<= asOf`, oldest
  first) — repository-tested directly against Postgres, alongside `findDebtorSnapshot`.
- The requester writes no audit row of its own: settlement (`INBOUND_COLLECTION_SETTLED`) or
  insufficient-funds return (`INBOUND_COLLECTION_RETURNED`) is decided and audited downstream on
  the issue-07 rail. A transient stub error is logged and skipped, leaving the schedule
  unadvanced for the next run to retry (the deterministic e2e id makes that safe).
- Fixed-amount mandates only (`amountMinorUnits!!`); a null amount throws a clear
  `IllegalStateException` (variable mandates are out of scope, headroom only).
- New client seam: `MalaiStubClient.postCollectionBacs(CollectionForwardPayload)` →
  `POST /scenarios/collections/bacs` (204), with `WithMalaiStub.postCollectionBacsAccepts()`
  test-support and a client-level request-construction test. No openapi.yaml change (stub +
  internal only, outside `/api/v1/**`) so oasdiff stays clean; no migration (V10+V18 carry
  every column).

Files:
- malai-stub-client: `collection/model/CollectionForwardPayload` (new),
  `MalaiStubClient.postCollectionBacs`; test-support `WithMalaiStub.postCollectionBacsAccepts`;
  client test `postsCollectionBacsWithExpectedBody`.
- direct-debit-service: `scheduler/DirectDebitCollectionRequester`,
  `service/DirectDebitCollectionRequestService` (new); `DirectDebitsRepository`
  (+findDueActiveMandates, +findDebtorSnapshot, +DebtorSnapshot); `DirectDebitServiceConfiguration`
  (@Import MalaiClientConfiguration); pom (+malai-stub-client main, +malai-stub-client-test-support
  test); application.properties (+malai.stub.base-url, +cron).
- Tests: happy `DirectDebitCollectionRequesterTest` (due-fires + deterministic e2e + advance
  full-entity DB assert, overdue one-fire resync, re-fire same-e2e dedup), sad
  `DirectDebitCollectionRequesterTest` (cancelled never scanned + unchanged full-entity assert),
  repository `DirectDebitsRepositoryTest` (due-scan: due/future/cancelled/null/ordering +
  debtor-snapshot); `WithDirectDebits` seeds an `account_identifiers` row + debtor params + a
  next-collection reset helper; `DirectDebitsAcceptanceTest` mixes in `WithMalaiStub`, maps the
  stub party, threads `stubBaseUrl`; `ApplicationRunSupport` (+stubBaseUrl); test props disable
  cron. malai-api application.properties + aggregator test props add the requester cron.

Verification: direct-debit-service green (41 tests: 3 happy + 1 sad requester, 6 repository,
5 happy + 4 sad controller, 22 arch); malai-stub-client green (4, incl. new collection test);
sandbox-service green (12) and standing-order-service green (56) — unaffected consumers of the
shared stub client; malai-api aggregator boots green (21) with the requester wired in and cron
disabled. Full reactor compiles + installs.

Deferred (environmental): the last AC — the literal cross-stack "verifiable whole" (seed a due
mandate, watch the settled collection + feed line through the full running stack) — needs all
services + localstack + Cognito stub + AWS creds, not reproducible here (same limitation as
issues 06/07's last AC). The requester is already driven end to end under stubbed time with the
real stub origination call asserted via WireMock, and issue 07 verifies the rail it lands on, so
the tracer logic is fully covered; only the live multi-service HTTP + feed poll remains. Issue 09
(sandbox mandate lodgement + DD e2e) is now unblocked — it lands mandates that collect on this
requester's clock.

## Blocked by

- 02 — cancel a direct debit (status and anchor columns)
- 07 — inbound BACS direct-debit rail (somewhere for the collection to land)
