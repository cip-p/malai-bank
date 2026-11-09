# 02 — mdr-service tracer: record a COMPLETED Model Decision end-to-end

Status: done

## Parent

`.scratch/mdr/PRD.md` (Model Decision Record)

## What to build

The first tracer through a brand-new `mdr-service`: a service JWT holder POSTs to the internal decisions endpoint and a COMPLETED Model Decision Entry lands chained under scope `mdr`. Carries the module bootstrap (registration per convention, own Spring configuration, architecture test) and the single migration creating the complete Entry table — all columns including the disposition side, so later slices need no schema change. Glossary vocabulary throughout: Entry, Model Decision, Outcome, Rendered prompt, Raw output, Correlation ref.

## Acceptance criteria

- [ ] Migration creates the entries table with kind/outcome CHECK shapes, unique seq, indexes (decision+seq, correlation+ts, parent link), UPDATE/DELETE/TRUNCATE revoked from the application role, and the `mdr` genesis chain-head row (32 zero bytes, seq 0); no partitioning (deliberate, per plan)
- [ ] `POST /api/v1/internal/mdr/decisions` (service JWT, internal path) records a COMPLETED Model Decision: model id + version, prompt-template id/version/hash, Rendered prompt inline, retrieved-context refs with content hashes, Raw output, structured verdict, latency, cost (micros + Currency enum), Correlation ref
- [ ] The Entry is chained via the chain core: seq advances from genesis, current hash = SHA-256(prev || canonical payload), head row advanced — asserted in an acceptance test
- [ ] Rendered prompt, Raw output, and verdict pass through the platform redactor before hashing and persisting
- [ ] A replayed request with the same entry id returns the existing Entry — natural idempotency, no duplicate append, no Idempotency-Key header (deliberate deviation, per PRD)
- [ ] The mutation records an ordinary audit event on the `global` scope; the happy-path controller test asserts on audit per convention
- [ ] Kensa acceptance tests: strict-match response contract (noExtraProps) and full-entity database assertion; sad paths for validation failures and missing service scope
- [ ] The service's ArchitectureTest runs the full rule set and passes
- [ ] Module registered (root build modules + dependency management, aggregator dependency + configuration import); test-support `With` interface and builders in place; full build green

## Blocked by

- `01-chain-core-extraction.md`

## Done (2026-07-18)

The tracer records a COMPLETED Model Decision end-to-end and is exercised by the
mdr-service acceptance suite (24 tests green; targeted regression net, DB up).

Delivered:
- `V25__create_mdr_entries.sql` — full table (all disposition-side columns), the 9
  CHECK constraints (kind/outcome/disposition values, kind shape, outcome shape,
  edit shape), unique seq, the three indexes, self-FK, append-only grants, and the
  `mdr` genesis chain-head row (32 zero bytes, seq 0). No partitioning (deliberate).
- `mdr-service` module: `MdrServiceApplication`/`Configuration`,
  `InternalMdrDecisionsController` (`POST /api/v1/internal/mdr/decisions`, service
  JWT), `MdrDecisionService` (idempotency + audit double-entry), `MdrEntryWriter`
  (chain-core append on scope `mdr`, mirrors `HashChainedAuditWriter`),
  `MdrEntryCanonicalJson`/`MdrEntryFields` (fixed hash projection shared by writer +
  the in-test verifier), request/response DTOs.
- Registration: root `pom.xml` modules + depMgmt, `malai-api` dependency +
  `@Import(MdrServiceConfiguration)`, `MalaiParty.MdrService`.
- Test support: `MdrAcceptanceTest`, `WithMdrEntries` (genesis reset, full-entity
  read, in-test hash recompute), `RecordModelDecisionRequestBuilder`,
  `MdrServiceArchitectureTest` (full rule set, 19 rules).

Deviations / notes for next slices:
- **No JPA entity/repository yet.** Write + idempotency-read + DB assertion all go
  through raw JDBC, mirroring the audit plane exactly (which has no entity). The
  typed read model belongs to issue 06 (the replay read) — its first real consumer —
  where the JSONB/`ByteArray` mapping choices can be made against a concrete need.
- **`MdrEntryWriter.record` only (REQUIRED).** The `recordIndependently`
  (REQUIRES_NEW) path + the rolled-back-business-tx survival test are issue 03's
  core criterion; there is no business tx to roll back in this slice.
- **`ModelDecisionOutcome` enum defined with all three values**, but only COMPLETED
  is exercised. `rawOutput` is `@NotBlank` for now (the COMPLETED shape); issue 03
  relaxes this for TIMED_OUT/ERRORED and adds the shape-contradiction sad paths.
- **Redaction routes through `AuditRedactor`** (text fields via a map-wrap, verdict
  and retrieved-context via `redact(Map)`); `AuditRedactor` itself is untouched.
  The redaction test proves rendered-prompt/raw-output/verdict are stripped before
  hashing (the recomputed chain hash matches the redacted form).
- **`MalaiParty.MdrService`** is the only issue-07 bleed — a single companion val so
  the acceptance base has a lane. The 7-file lane wiring (classifiers, lookups,
  prefixes) stays in issue 07.
- **malai-api's stale "doesNotDependOnDomain" allowlists** were left as-is (they
  already omit ledger/card/insights/sandbox); a broader aggregator cleanup, not this
  slice.
