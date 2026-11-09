# MDR (Model Decision Record) — design plan

Status: shipped to `main` on 2026-07-18 (commits `ed71ecd7`..`c20932ee`, fast-forwarded; issues 01–07 in `issues/done/`). Decisions below were grilled one-by-one; the four foundational ones were user-confirmed, and the convention-forced *(review item)* ones were resolved on acceptance.

Ops-readiness follow-up shipped the same day (issues 08–10 in `issues/done/`): the admin Decision worklist (`GET /api/v1/admin/mdr/decisions`, `rulingState` filter incl. `AWAITING_RULING`), a seeded ops reviewer in the `admin` Cognito group, and seeded mixed-state MDR demo data appended through the service layer with fixed entry ids (idempotent replay). These are the backend prerequisites for the webapp ops pages.

Companion documents: `docs/adr/0007-mdr-is-a-service-module-with-its-own-chain-scope-on-an-extracted-chain-core.md` (the load-bearing decision), `malai-services/mdr-service/CONTEXT.md` (glossary).

## Confirmed decisions

1. **Chain shape** — new typed `mdr_entries` table chained under a second `chain_head` scope (`'mdr'`); chain mechanics (canonical JSON, SHA-256 step, `FOR UPDATE` head advance, batch replay verification) extracted from `com.malai.security.audit` into a generalised core in `malai-security` shared by both chains. Not riding the audit chain (lock contention, no typed schema); not duplicating (drifting crypto).
2. **Module home** — full `malai-services/mdr-service` module with the complete convention set. No `malai-mdr-client` pair yet: client modules exist only where internal call-outs exist, and nothing calls the MDR this increment.
3. **Entry model** — one heterogeneous table, one chain. `entry_kind` (`MODEL_DECISION` | `HUMAN_DISPOSITION`), self-referencing `parent_entry_id` CHECK-required for dispositions. A disposition's hash transitively covers the decision it rules on, so "this human saw exactly that output" is proven by the chain itself.
4. **Failure model** — exactly one `MODEL_DECISION` entry per invocation, appended when it concludes, with `outcome` `COMPLETED | TIMED_OUT | ERRORED` and CHECK-tied shape. Written on the `REQUIRES_NEW` path so a rolled-back business tx still leaves the record. Documented limitation: a process crash mid-call leaves no entry — mitigated later by ArchUnit-enforced wrapper reachability (the `mutatingControllersReachAuditWriter` trick), not by protocol.
5. **Redaction** — model output is redacted via the existing `AuditRedactor` *before* hashing and persisting, exactly as the audit chain treats payloads. "Raw output" = verbatim modulo platform redaction.
6. **Replay** — the fully rendered (redacted) prompt is stored inline; retrieved-context as refs + per-item content hashes; prompt template pinned by id + version + hash (templates live in git). The record alone answers *what* the model saw and produced; refs answer *where it came from*.
7. **Ruling governance** — latest disposition wins. A mistaken accept is corrected by appending a reject. Actionable output is evaluated against the ruling in force at read time; chain order + timestamps prove what was in force at any past moment.
8. **Synthetic marker** — satisfied architecturally, like every other table (no marker exists anywhere in the platform; adding one only here would imply the rest is marked). A platform-wide marker, if wanted, is its own increment.

## Schema — `V25__create_mdr_entries.sql` (single migration)

```sql
CREATE TABLE mdr_entries (
    entry_id                UUID PRIMARY KEY,
    ts                      TIMESTAMPTZ NOT NULL,
    entry_kind              VARCHAR(20) NOT NULL,       -- MODEL_DECISION | HUMAN_DISPOSITION
    decision_id             UUID NOT NULL,
    parent_entry_id         UUID REFERENCES mdr_entries (entry_id),
    correlation_type        VARCHAR(40) NOT NULL,       -- CASE | PAYMENT | ACCOUNT | ... (opaque, no cross-module FK)
    correlation_id          VARCHAR(80) NOT NULL,

    model_id                VARCHAR(120),
    model_version           VARCHAR(60),
    prompt_template_id      VARCHAR(120),
    prompt_template_version VARCHAR(60),
    prompt_template_hash    BYTEA,
    rendered_prompt         TEXT,                        -- redacted, inline (replay)
    retrieved_context       JSONB,                       -- [{type, id, content_hash}]
    outcome                 VARCHAR(20),                 -- COMPLETED | TIMED_OUT | ERRORED
    raw_output              TEXT,                        -- redacted; NOT NULL iff COMPLETED
    verdict                 JSONB,                       -- structured verdict; nullable even when COMPLETED (unparseable output is a recorded fact)
    error_detail            TEXT,                        -- NOT NULL iff ERRORED
    latency_ms              BIGINT,
    cost_micros             BIGINT,
    cost_currency           VARCHAR(3),

    human_id                UUID,                        -- disposition author
    disposition             VARCHAR(10),                 -- ACCEPT | EDIT | REJECT
    edited_output           TEXT,                        -- redacted full replacement; NOT NULL iff EDIT (diff derived at read)

    prev_hash               BYTEA  NOT NULL,
    current_hash            BYTEA  NOT NULL,
    seq                     BIGINT NOT NULL UNIQUE
);
```

CHECK constraints (shape per kind — the point of the single-table model):
- `MODEL_DECISION` ⇒ `outcome, model_id, model_version, prompt_template_*, rendered_prompt` NOT NULL; `parent_entry_id, human_id, disposition, edited_output` NULL.
- `HUMAN_DISPOSITION` ⇒ `parent_entry_id, human_id, disposition` NOT NULL; all model-side columns NULL.
- `outcome = 'COMPLETED'` ⇔ `raw_output` NOT NULL; `outcome = 'ERRORED'` ⇔ `error_detail` NOT NULL.
- `disposition = 'EDIT'` ⇔ `edited_output` NOT NULL (the full replacement is the hash-covered actionable artifact; the diff is derived at read time from two immutable inputs, never stored, never applied).

No separate `actor` column: actor kind (model | human) is fully implied by `entry_kind`; model identity lives in `model_id`/`model_version`, human identity in `human_id`.

Indexes: `UNIQUE (seq)` (above), `(decision_id, seq)`, `(correlation_type, correlation_id, ts DESC)`, `(parent_entry_id)`.

Append-only enforcement, copied from V7: `REVOKE UPDATE, DELETE, TRUNCATE ON mdr_entries FROM PUBLIC, malai_app; GRANT SELECT, INSERT TO malai_app;`

Chain bootstrap: `INSERT INTO chain_head VALUES ('mdr', <32 zero bytes>, 0);`

**Deliberate deviation — no `ts` partitioning** *(review item)*: audit's monthly partitions exist for platform-wide business-write volume and forced a composite PK plus a partition-management job that was never built (pre-created only to 2027-12). MDR volume is demo-scale; a plain table keeps a real PK for the self-FK and avoids inheriting the time bomb. Revisit alongside audit's multi-shard work.

Hash payload: canonical projection of all semantic fields, excluding `entry_id`, `seq`, `prev_hash` (same exclusion rationale as audit — writer/DB-assigned identity out, content in). `current_hash = SHA-256(prev_hash || canonical_json(entry))` via the shared core.

## Chain-core extraction (`malai-security`)

New package `com.malai.security.chain`:
- `ChainHead` DAO — `lockAndRead(scope)` (`SELECT … FOR UPDATE`), `advance(scope, hash, seq)`.
- `ChainHasher` — `SHA-256(prev || canonicalBytes)`; `CanonicalJson` moves here (audit keeps a type alias or delegating import).
- `ChainVerifier` — batch walk generalised over a row source (`(scope, table, canonical projection)`); result type unchanged (`Ok | GapAtSeq | PrevHashMismatch | CurrentHashMismatch | HeadMismatch`).

`HashChainedAuditWriter` and `AuditChainVerifier` delegate to the core. **Contract: audit hashes are byte-for-byte identical before and after** — the existing harness (`HashChainedAuditWriterTest`'s in-test hash recomputation, `AuditChainVerifierTest` tampering, `CanonicalJsonTest`) is the regression net. `AuditWriter`'s FQN does not move (pinned by the ArchUnit rule).

`mdr-service` adds `MdrEntryWriter` (same `record` / `recordIndependently` transactional pair) using the core with scope `'mdr'`, redacting `rendered_prompt`, `raw_output`, `verdict`, `edited_output` through `AuditRedactor` before hash + persist.

## Module layout

```
malai-services/mdr-service/
  src/main/kotlin/com/malai/mdr/
    MdrServiceConfiguration.kt        @ComponentScan/@EnableJpaRepositories/@EntityScan + MalaiSourceRoutes
    controller/  InternalMdrDecisionsController, MdrAdminController
    service/     MdrEntryWriter, MdrDecisionService (gate logic), MdrChainVerificationService
    repository/  MdrEntryRepository (custom finders → repository tests required)
    entity/      MdrEntryEntity (Currency enum @Enumerated(STRING) for cost_currency)
    model/       request/response DTOs
  src/test/kotlin/com/malai/mdr/
    architecture/ MdrServiceArchitectureTest          (full rule set — unlike ledger, no gap)
    acceptance/   happy/ + sad/ per convention
    support/      WithMdrService, NoOpDecider
  CONTEXT.md                                          (written)
  docs/adr/                                           (empty for now; 0007 is system-wide)
```

Registration touches: root `pom.xml` `<modules>` + `<dependencyManagement>`; `malai-api/pom.xml` dependency (+ test-jar entry); `@Import(MdrServiceConfiguration::class)` in `MalaiApiApplication`.

## API surface (no `openapi.yaml` change — contract excludes `/api/v1/internal/**`, and this increment has no customer-facing surface)

| Endpoint | Auth | Purpose |
|---|---|---|
| `POST /api/v1/internal/mdr/decisions` | service JWT (`@InternalEndpoint`) | append a MODEL_DECISION entry |
| `POST /api/v1/admin/mdr/decisions/{decisionId}/dispositions` | `hasRole('ADMIN')` | append a HUMAN_DISPOSITION |
| `GET /api/v1/admin/mdr/decisions/{decisionId}` | `hasRole('ADMIN')` | the full entry thread — the replay read |
| `GET /api/v1/admin/mdr/decisions/{decisionId}/actionable-output` | `hasRole('ADMIN')` | the gate: 409 until a ruling exists; serves per ruling in force |
| `GET /api/v1/admin/mdr/chain-verification` | `hasRole('ADMIN')` | verify scope `'mdr'` (mirrors audit's admin endpoint) |

*(review item)* **Idempotency**: the platform invariant says production write endpoints take `Idempotency-Key`, but that package has an open critique to be surfaced before new uses. Recommendation: **client-supplied `entry_id` as natural idempotency instead** — a replayed append hits the PK and returns the existing entry; a response-snapshot mechanism is redundant for an append with intrinsic identity. This is a deliberate, argued deviation; veto if you want the header convention regardless.

Verification tooling *(review item)*: `verify-chain` CLI and the hourly `AuditChainVerifierJob` generalise to iterate every `chain_head` scope (one job, per-scope results, same ShedLock name); CI keeps one gate covering both chains.

Audit-rule compliance: both MDR mutating controllers also record ordinary audit events (`MDR_DECISION_RECORDED`, `MDR_DISPOSITION_RECORDED`) — satisfying `mutatingControllersReachAuditWriter` rather than exempting it. Audit chain = who did what; MDR chain = what was decided. Deliberate double-entry, not duplication.

Actor vocabulary *(review item)*: MDR does not extend audit's `ActorType`; actor kind is implied by `entry_kind` (see schema). The `CONTEXT.md` flagged ambiguity resolves to "standalone" on acceptance.

**No-op decider**: no production `Decider` interface this increment — an interface with only a test implementation is dead production code, and the seam belongs to the first real consumer. The "no-op decider" is test support (`TestPanGenerator` pattern): a `NoOpDecider` in `mdr-service` test support that drives the internal write endpoint the way a real integration would, deterministically (fixed verdicts, controllable outcome incl. TIMED_OUT).

## Migration list

1. `V25__create_mdr_entries.sql` — table + CHECKs + indexes + grants + `chain_head` `'mdr'` genesis row. That's the whole list; the chain core needs no schema change (`chain_head.scope` already exists).

## Test list

**malai-security (regression net for the extraction)** — existing `HashChainedAuditWriterTest`, `AuditChainVerifierTest`, `AuditChainVerifierJobTest`, `CanonicalJsonTest`, `AuditRedactorTest` all stay green untouched; add a chain-core test proving two scopes advance independently (append to both, verify both, cross-check no interference).

**mdr-service acceptance (Kensa, happy/sad per convention)**
- Record COMPLETED decision → 201, Kacey `noExtraProps` response, full-entity DB assertion (`theMdrEntryInTheDatabaseIs(...)` with every column — new fields force test updates).
- Record TIMED_OUT and ERRORED decisions → entry exists, `raw_output` null, outcome CHECK shape asserted.
- Append ACCEPT / EDIT (with full edited output) / REJECT dispositions → chained after the decision; `parent_entry_id` + hash linkage asserted.
- Latest-wins: ACCEPT then REJECT → actionable-output 409; ACCEPT after REJECT → serves again.
- Gate: no disposition → 409; EDIT → the stored edited output served verbatim; replay read includes the derived diff.
- Chain integrity: mixed-kind entries chain correctly; tamper a `current_hash` at a chosen seq → verification endpoint reports `CurrentHashMismatch` (mirror of the audit tamper test).
- Scope isolation: MDR writes leave the audit chain head untouched and vice versa.
- `recordIndependently`: business tx rollback still leaves the MDR entry.
- Sad counterparts (ArchUnit-enforced): validation failures, unknown `decision_id`, disposition on a disposition, non-ADMIN, missing service scope, replayed `entry_id` → idempotent response.
- `MdrServiceArchitectureTest`: full rule set, including `controllerTestsAssertOnAudit` (MDR mutations write audit events) — and repository tests for every custom finder.

**e2e (the brief's named flow)** — `MdrTest`: write a model decision through the internal endpoint (the test JVM acting as the no-op decider), append a human disposition, hit chain verification → `Ok`, with DB assertions on the chained rows. Requires the 7-file diagram-lane wiring: `MalaiParty.Mdr`, `partyFromSource`, `fromSystemLookup`, `toSystemLookup`, `HttpClassifier` mdr classifiers, `InteractionDescriber.servicePrefixes`, plus `WithMdrService` e2e helper + RestClient wiring (`SqsClassifier` not needed — no queues).

**CLI/job** — seeder-module test: `verify-chain` exits 0 with both scopes intact, 1 when either is tampered.

## PR sequence (smallest first PR that delivers something real)

1. **PR1 — chain core extraction.** `com.malai.security.chain`, audit writer/verifier delegate, CLI + job scope-generalised. Zero behaviour change, hashes byte-identical, every existing audit test green. Real because the platform gains proven multi-chain capability — the MDR's foundation — with the regression net as evidence.
2. **PR2 — the MDR write plane.** `V25`, `mdr-service` module + registration, `MdrEntryWriter`, internal POST, acceptance tests incl. tamper + scope isolation, `MdrServiceArchitectureTest`.
3. **PR3 — dispositions + the gate.** Disposition endpoint, replay read, actionable-output gate with latest-wins, admin chain-verification endpoint.
4. **PR4 — e2e + diagram lane.** The brief's named e2e flow plus the 7-file lane wiring.

## What in the existing architecture makes this awkward (recap of Phase 1 findings)

The brief's premise required correction: the ledger is not hash-chained — the chain is the audit plane's, so "reuse the ledger's chaining" became "become the audit chain's second tenant" (ADR-0007). The synthetic marker the brief cites does not exist anywhere. The audit chain is a single globally-locked chain whose writer/verifier are hard-wired to one table — reuse means refactoring shared security-critical code (derisked by its tamper-test harness). "No output actionable without disposition" has no consumer to enforce against, so it lives at the MDR read surface until ArchUnit can police real consumers. Module fencing is only half-enforced today (7/15 services have ArchitectureTests; `MalaiApiArchitectureTest` package lists are stale) — mdr-service should not inherit that gap. The `mutatingControllersReachAuditWriter` rule forces MDR mutations onto the audit chain too — embraced as deliberate double-entry. Audit's partition scheme carries an unbuilt partition-management job — avoided by not partitioning at demo scale.
