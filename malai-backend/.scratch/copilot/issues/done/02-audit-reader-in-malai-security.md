# 02 — An AuditReader beside the AuditWriter

Status: ready-for-human
Category: enhancement

## What to build

The audit plane can be written and verified but not read: `malai-commons/malai-security/audit/` has `AuditWriter`, `HashChainedAuditWriter` and `AuditChainVerifier`, and nothing that answers "what happened to this resource". Add `AuditReader` beside the writer — a query by `(resourceType, resourceId)` returning the events in chain order (ascending `seq`), each as an immutable projection carrying `eventId`, `ts`, `actorId`, `actorType`, `action`, `resourceType`, `resourceId`, `beforeState`, `afterState`, `traceId` and `seq`.

Audit is shared infrastructure with no owning bounded context — `CONTEXT-MAP.md` says so outright — so a shared reader is symmetric with how every service already writes directly, and no service becomes the de facto owner of the table. Reads take no chain lock, so the loopback-audit deadlock trap does not apply; keep it that way (no write, no `SELECT … FOR UPDATE`).

Scope the read: a window parameter (`from`/`until`) and a result cap, so a caller cannot accidentally pull a partition's worth of rows.

## Acceptance criteria

- [x] `AuditReader` returns every event for a `(resourceType, resourceId)` in ascending `seq`, pinned by a test over seeded events
- [x] The projection carries the full row shape listed above and exposes no mutation surface
- [x] The window and cap parameters are honoured, with a documented default cap
- [x] An unknown resource returns an empty list, not an error
- [x] The reader takes no lock and issues no write — asserted structurally in the security module's ArchUnit test
- [x] A repository/reader test covers the custom finder, per the house rule
- [x] `mvn install` green

## Blocked by

Nothing.

## Resolution

`AuditReader` landed in `com.malai.security.audit` beside the writer, with `AuditEventRecord` as its
projection. `openapi.yaml` has a 0-line diff — the reader is infrastructure with no HTTP surface;
issue 04 is its first consumer.

**Wiring is free.** `MalaiSecurityConfiguration` already `@ComponentScan`s `com.malai.security`, so
the `@Service` is a bean in every service that imports it — no per-module registration, no
base-url property, none of the usual new-collaborator tax.

Decisions worth recording:

- **The cap is both the default AND the ceiling.** `(limit ?: maxEvents).coerceAtMost(maxEvents)` —
  a caller may ask for fewer, never for more. A `limit` that could exceed the configured value would
  make the cap decorative, which defeats the point of the issue's "cannot accidentally pull a
  partition's worth of rows". Default 500 (`DEFAULT_MAX_EVENTS`, overridable via
  `audit.read.max-events`), sized two orders of magnitude above the busiest resource this platform
  produces — a payment settles in single-digit events, a referred one in low tens — so it bounds a
  runaway read without ever truncating a real investigation.
- **Truncation keeps the earliest events, not the newest.** `ORDER BY seq ASC LIMIT n`. Chain order
  is the order things happened, and an investigation reads a resource's story from its beginning.
- **A `limit` of zero or fewer is rejected**, not silently read as the cap. Coercing it up would hand
  back 500 rows to a caller that asked for none.
- **The window is half-open — `[from, until)`** — matching how `audit_events`' monthly ranges are
  declared in V7, so a window aligned to a partition boundary reads exactly that partition. Both
  sides are `ts` predicates rather than a post-filter precisely so they prune partitions; confirmed
  by `EXPLAIN`, which prunes a month-wide window to a single partition and index-scans it with no
  sort.
- **Immutability is real, not asserted in prose.** The projection is `val`-only, and the JSONB state
  maps are wrapped `Collections.unmodifiableMap` — Kotlin's read-only `Map` alone would still let a
  holder cast to `MutableMap` and edit the record of what happened. Pinned by a test that does
  exactly that cast and expects `UnsupportedOperationException`.
- **The ArchUnit rule denies a *dependency*, not just a call.** `auditReadersNeitherLockNorWrite`
  fails any `com.malai.security.audit..` class named `*Reader` that calls
  `JdbcTemplate.update/batchUpdate/execute` **or** depends on `ChainHeads`/`ChainAppender` — the only
  path to the `SELECT … FOR UPDATE` in this codebase. Checking dependencies rather than method calls
  catches construction (`ChainAppender(ChainHeads(jdbc))` is how the writer takes the lock, and that
  is a constructor call ArchUnit's `callMethodWhere` would miss). Red baseline proved rather than
  assumed: temporarily adding a `lockAndRead` + `jdbc.update` to the reader failed the rule 3 times;
  reverted, green.
- **The reader's test lives in `auth-service`'s acceptance package**, beside `HashChainedAuditWriterTest`
  and `AuditChainVerifierTest`. `malai-security`'s own tests are plain JUnit + Mockito with no
  Postgres — the module has no DB harness, and the established answer to that (both existing
  chain-integration tests) is to piggyback auth-service's booted context. Not inventing a third
  pattern.

**One migration added beyond the issue's letter: `V26__index_audit_events_by_resource.sql`.** The
reader's only access path — `WHERE resource_type = ? AND resource_id = ?` — had no supporting index;
V7 covers `(actor_id, ts)`, `trace_id` and `seq` only, so every read would have sequential-scanned
every partition. `(resource_type, resource_id, seq)` on the partitioned parent, which PG 11+
propagates to all 25 children — 24 monthly (`2026_01`…`2027_12`) plus `audit_events_default`
(verified: 26 rows in `pg_indexes` matching the index definition, the 25 children and the parent);
`seq` trails the equality columns so the index also serves `ORDER BY seq ASC` without a sort. `ts` is
deliberately absent — the window prunes whole partitions before the index is consulted.
Backwards-compatible, no data change.

**Comments stripped from both new Kotlin files before landing.** The first draft carried the
reasoning above as block comments, which the cross-cutting no-comments invariant forbids — the
`audit` package's commented neighbours (`ActorType`, `HashChainedAuditWriter`, `AuditTestSupport`)
predate the rule, and every file added by issue 01 carries none. The naming now carries the intent
(`DEFAULT_MAX_EVENTS`, `eventsFor`, `from`/`until`/`limit`, `coerceAtMost(maxEvents)`) and this
resolution carries the rationale. The migration keeps its header comment — SQL migrations are
commented throughout, V7 included.

On "documented default cap": the property is declared inline as
`@Value("\${audit.read.max-events:$DEFAULT_MAX_EVENTS}")`, matching how `ChainVerificationJob`
declares `audit.verify.scheduler.cron` in the same module. This repo has no central property doc,
so the named constant plus the `audit.*` namespace is the convention, not an omission.

Verified: red baseline first at every step — the tracer failed to compile (no `AuditReader`), the
window and cap params failed to compile before they existed, and the ArchUnit rule was proved to
bite before being trusted (a temporary `jdbc.update` + `ChainHeads` dependency failed it 3 times;
reverted, green). `mvn install` BUILD SUCCESS across all 56 modules, 1h00m, zero failures —
`AuditReaderTest` 11/11, `MalaiSecurityArchitectureTest` 7/7, and the existing
`HashChainedAuditWriterTest` 7/7 and `AuditChainVerifierTest` 8/8 still green beside it. The index
propagation and partition pruning were confirmed directly against Postgres rather than taken on
trust: 26 matching `pg_indexes` rows over 25 children, and a month-wide window planning as a single
`audit_events_2026_05` index scan with no Sort node.

## Notes for the next iteration

- **Issue 04 is now unblocked on two of its three prerequisites** (01 and 02). Issue 03 (MDR entries
  declare `advisory`) is still open and independent — it is the last thing 04 waits on.
- The reader answers `(resourceType, resourceId)` only. Issue 06 wants a referred payment's screening
  story from the audit plane, and every event it names (`PAYMENT_REFERRED`, `MDR_DECISION_RECORDED`,
  `MDR_DISPOSITION_RECORDED`, `PAYMENT_RELEASED`, `PAYMENT_DECLINED`) is written against
  `resourceType = "Payment"`, so one call per subject covers it. If a later issue needs a
  cross-resource sweep (by `traceId`, say), that is a second finder — `idx_audit_events_trace` already
  exists for it.
- No consumer was added here. The reader has no call site until issue 04 assembles the Timeline, so
  it is currently exercised only by its own test.
- **Only the top level of a state map is unmodifiable.** `Collections.unmodifiableMap` does not wrap
  nested values, so a JSONB object nested inside `beforeState`/`afterState` would still be castable
  to `MutableMap`. Not a real gap today — every production `afterState` in the platform is a flat
  scalar map (`PaymentLifecycleService`, `PaymentsService`, `PayeesService` all checked) — so a
  recursive wrap would be defending against data that does not exist. If a writer ever nests, deepen
  the wrap and extend the immutability test to match.
