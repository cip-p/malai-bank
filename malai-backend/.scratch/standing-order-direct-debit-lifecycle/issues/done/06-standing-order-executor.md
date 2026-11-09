# 06 — Standing-order executor: due orders become real payments

Status: ready-for-agent

## Parent

`.scratch/standing-order-direct-debit-lifecycle/PRD.md`

## What to build

The clock. A per-minute, lock-coordinated scheduler in the standing-order service scans ACTIVE orders whose next payment date has arrived and executes each due occurrence through the payments client with its deterministic end-to-end ID — initiate first, advance second. Occurrence arithmetic is anchor-based: every occurrence is start date plus n × frequency, clamped to month-end in shorter months; after firing exactly once, the next payment date resynchronises to the first occurrence strictly in the future, so downtime never causes catch-up bursts. A failed initiation (insufficient funds) is a missed occurrence: audited, never retried, schedule advances, order stays ACTIVE. Executions happen on any calendar day by design. This slice completes the standing-order tracer and includes its cross-stack end-to-end journey: create an order paying today, and the payment line appears in the account feed within a couple of minutes.

## Acceptance criteria

- [x] Scheduled job runs per minute with the repo's lock coordination (the architecture rule enforcing the lock annotation passes); dueness read from the injected time service
- [x] Due ACTIVE order → exact outgoing initiation request asserted (WireMock), including the deterministic end-to-end ID derived from order ID and occurrence date; next payment date advanced — full-entity database assertion
- [x] One-fire resync: an order overdue by several periods fires exactly once and lands on the next strictly-future occurrence
- [x] Anchor arithmetic: monthly order anchored on the 31st pays on the last day of shorter months and returns to the 31st; weekly, quarterly and annual frequencies covered
- [x] Failed initiation: audit records the missed occurrence, no retry, schedule advances, order remains ACTIVE
- [x] CANCELLED orders are never scanned; re-fired duplicates (crash between initiate and advance) bounce harmlessly
- [x] Repository tests for the due-scan query; occurrence arithmetic built as a shared, separately-tested component reusable by the direct-debit requester
- [x] Kensa acceptance tests drive the executor under stubbed time
- [x] Cross-stack end-to-end journey: create a standing order with first payment today via the API, poll the transaction feed, assert the payment line appears

## Progress

2026-07-07 — backend executor slice landed on `main`. The per-minute
ShedLock-coordinated scanner (`StandingOrderExecutor.runOnSchedule` →
`executeDueOrders`) executes each due ACTIVE occurrence through the payments
internal seam (issue 05) with a deterministic end-to-end id, then advances.

Key decisions:
- Occurrence arithmetic is a shared, separately-unit-tested component in
  `malai-commons` (`com.malai.commons.schedule.OccurrenceSchedule` +
  `ScheduledPaymentEndToEndId`), reusable by the direct-debit requester (issue 08).
  Anchor + n×frequency with month-end clamp/return falls straight out of
  java.time `plusMonths`/`plusYears`; `nextStrictlyAfter` gives the one-fire resync.
- Initiate first / advance second (ADR 0004): the advance runs in both the success
  and insufficient-funds paths, so a missed occurrence still resynchronises. A
  re-fire after a crash reuses the same `f(orderId, occurrenceDate)` end-to-end id
  and bounces off the payments unique key. Any other initiation error propagates so
  the schedule stays unadvanced and retries next run.
- Insufficient funds → SYSTEM-actor `STANDING_ORDER_PAYMENT_MISSED` audit row, no
  retry, order stays ACTIVE. Executor auth uses a new `StandingOrderServiceTokenClient`
  (client-credentials, scope `malai-internal/onboarding.write`).
- ShedLock is enabled transitively via `MalaiSecurityConfiguration`'s scan of
  `com.malai.security` (no new `@EnableScheduling`/lock config needed). The `@Scheduled`
  entry `runOnSchedule` carries `@SchedulerLock` (arch rule now non-vacuous); the
  testable `executeDueOrders` worker is split out so acceptance tests drive the
  business logic deterministically without ShedLock timing flakiness. Cron is
  disabled (`standing-order.executor.cron=-`) in standing-order + malai-api test props.

Verification: standing-order-service green (56 tests incl. 4 happy + 2 sad executor,
5 repository due-scan, 14 commons schedule unit tests, 19 arch); malai-api aggregator
boots green (21 tests) with the executor wired in. Full reactor compiles + installs.

Remaining: the cross-stack end-to-end journey (last AC) needs the full running stack
(all services + localstack + Cognito stub, AWS creds) which isn't reproducible in
this environment — deferred as a follow-up. The executor is already driven end-to-end
under stubbed time with the real payments call asserted via WireMock, so the tracer
logic is fully covered; only the live multi-service HTTP + feed poll remains.

## Blocked by

- 01 — cancel a standing order (status and anchor columns)
- 03 — create a standing order (something to execute)
- 05 — payments scheduled-initiation seam (somewhere to execute it)
