# PRD: Model Decision Record (MDR)

Status: ready-for-agent

Companions: `plan.md` (grilled design plan, same directory), ADR-0007 (chain-scope decision), `malai-services/mdr-service/CONTEXT.md` (glossary — its vocabulary is used throughout this PRD).

## Problem Statement

Malai's buyers — bank executives and technologists evaluating the platform — increasingly ask how AI-assisted decisions would be governed. Today the platform has no answer: the audit plane records business mutations and the ledger records money movements, but a model-produced verdict (a fraud recommendation, a case disposition) would leave no tamper-evident trace, no link to the human who approved it, and no way to reconstruct months later what the model saw and why it said what it said. Until that record exists, wiring any model into the platform would create ungoverned decisions — and a demo that cannot answer "how would you audit your AI?" loses the room.

## Solution

The Model Decision Record: an internal, back-office audit plane that records every model invocation as an immutable, hash-chained Entry — including invocations that timed out or errored, because a missing output is itself a recorded fact. Humans rule on Model Decisions by appending Human Disposition entries (accept / edit / reject); nothing is ever updated in place, and a mistaken ruling is corrected by appending a newer one. No model output is actionable until the Ruling in force allows it, enforced at the MDR's read surface. The chain is verifiable on demand (admin endpoint), on schedule (hourly job), and in CI (CLI exit code). The whole plane is provider-agnostic — no LLM dependency anywhere; a deterministic no-op decider in test support exercises every flow. It ships and is provably sound before any model exists to write to it.

## User Stories

1. As a compliance auditor, I want every model invocation recorded as an immutable Entry, so that no AI-assisted decision can exist off the record.
2. As a compliance auditor, I want each Entry hash-chained to its predecessor, so that tampering with any past decision is detectable by replaying the chain.
3. As a compliance auditor, I want a model timeout or error recorded as a Model Decision with an explicit Outcome, so that the absence of output is an auditable fact rather than a gap.
4. As a compliance auditor, I want the Rendered prompt stored inline in the Entry, so that I can reconstruct exactly what the model saw 18 months later without any other system surviving.
5. As a compliance auditor, I want retrieved-context recorded as Correlation-style refs with content hashes, so that I can prove whether today's copy of a source is what the model was shown.
6. As a compliance auditor, I want model id, model version, prompt-template id/version/hash, latency, and cost on every Model Decision, so that governance questions — which model, how slow, at what cost — are answerable from the record.
7. As a compliance auditor, I want the Ruling in force at any past moment provable from chain order and timestamps, so that an action taken under an earlier ruling stays defensible after later rulings change it.
8. As a back-office reviewer, I want to accept a Model Decision, so that its Raw output becomes the Actionable output.
9. As a back-office reviewer, I want to edit a Model Decision by recording my full edited output, so that a corrected artifact becomes actionable without the original ever changing.
10. As a back-office reviewer, I want to reject a Model Decision, so that its output can never be acted on.
11. As a back-office reviewer, I want to correct my own earlier ruling by appending a newer Disposition, so that human mistakes are recoverable in an append-only world.
12. As a back-office reviewer, I want the diff between the Raw output and an edited Disposition derived and shown at read time, so that I can review what changed without the diff being a stored artifact that could drift.
13. As an integrating service developer, I want a single actionable-output read that refuses until a qualifying Human Disposition exists, so that my service structurally cannot act on unreviewed model output.
14. As an integrating service developer, I want Model Decisions written through an internal endpoint with service credentials while Dispositions require a human admin, so that machine and human writers are separately authenticated and never confusable.
15. As an integrating service developer, I want writes with a repeated entry identity to be idempotent, so that retries never fork or duplicate the record.
16. As an integrating service developer, I want decisions tied to business resources by opaque Correlation refs, so that the MDR serves any domain — payments, cases, accounts — without schema coupling to it.
17. As a platform operator, I want an hourly scheduled verification that walks every chain scope, so that integrity failures surface loudly without anyone asking.
18. As a platform operator, I want a chain-verification CLI with a non-zero exit code on any failure, so that CI and deployments can gate on integrity.
19. As a platform operator, I want an on-demand admin verification endpoint for the MDR chain, so that I can prove integrity live during an incident or a demo.
20. As a platform operator, I want MDR appends serialised on their own chain scope, so that model-decision volume never contends with the audit chain that every business write holds.
21. As a security engineer, I want UPDATE, DELETE, and TRUNCATE revoked from the application role on the MDR table, so that append-only is enforced below the application layer, not by discipline.
22. As a security engineer, I want Rendered prompts, Raw outputs, verdicts, and edited outputs redacted before they are hashed and persisted, so that an immutable table can never become a permanent home for leaked sensitive material.
23. As a security engineer, I want MDR mutations to also appear on the platform audit chain, so that who-did-what and what-was-decided are independently evidenced on separate chains.
24. As a platform maintainer, I want the chain mechanics shared with the audit plane rather than duplicated, so that a fix to hashing or verification reaches every chain at once.
25. As a platform maintainer, I want the audit chain's hashes proven byte-for-byte unchanged by the extraction, so that reusing the machinery cannot silently rewrite history.
26. As a platform maintainer, I want the new module fully fenced and registered per convention — including an architecture test, which some existing services lack — so that the MDR strengthens the architecture instead of diluting it.
27. As a test author, I want a deterministic no-op decider in test support, so that every MDR flow is exercisable with zero model or provider dependency.
28. As a demo presenter, I want the e2e flow — model decision written, human disposition attached, chain verified intact — recorded as a sequence diagram with the MDR as its own lane, so that the governance story is visible in the platform's flow documentation.
29. As a bank decision-maker watching a demo, I want tamper-detection demonstrated live (corrupt a row, watch verification name the sequence number), so that I can judge whether this platform would satisfy my regulators.
30. As a bank decision-maker, I want the MDR provider-agnostic, so that adopting the platform never couples my bank to a model vendor.

## Implementation Decisions

All decisions below were grilled with the client one-by-one; the foundational four are recorded in ADR-0007 and the glossary.

**Chain architecture.** The MDR is the second tenant of the platform's existing hash-chain machinery (the audit plane's — note the ledger has no hash chain; that premise in the original brief was corrected). The chain mechanics — canonical JSON, `SHA-256(prev_hash || payload)`, head row locked `FOR UPDATE`, batch replay verification — are extracted from the audit code in the shared security module into a chain core with a two-method surface (append to scope, verify scope). The audit writer and verifier delegate to it; the audit writer's public type does not move (an ArchUnit rule pins its name). The MDR gets its own head row (scope `mdr`, genesis 32 zero bytes at seq 0), so MDR appends never contend with the audit chain's global lock.

**Module.** A full `mdr-service` under the services tree with the complete convention set: own Spring configuration imported by the aggregator, root build registration, its own architecture test, a diagram party lane, and its glossary. No client-module pair yet — client pairs exist only where internal call-outs exist, and nothing calls the MDR in this increment.

**Entry model.** One heterogeneous table, one chain. Every row is an Entry of kind MODEL_DECISION or HUMAN_DISPOSITION, with a self-referencing parent link CHECK-required for dispositions. Because dispositions chain after the decisions they rule on, a disposition's hash transitively covers the decision — the chain itself proves "this human saw exactly that output." Shape per kind is enforced by CHECK constraints (from the grilled schema): model-side fields (outcome, model id/version, prompt-template pin, rendered prompt) are required on decisions and null on dispositions; disposition-side fields (parent link, human id, disposition kind) vice versa; output presence is tied to Outcome (`COMPLETED` ⇔ raw output present, `ERRORED` ⇔ error detail present); `EDIT` ⇔ full edited output present. Actor kind is implied by entry kind — no separate actor column. Cost is carried as micros plus a currency code using the platform Currency enum convention.

**Failure semantics.** Exactly one Model Decision Entry per invocation, appended when it concludes, whatever the Outcome. Written on the independent (`REQUIRES_NEW`-style) path so a rolled-back business transaction still leaves the record. Documented limitation: a process crash mid-invocation leaves no Entry; the countermeasure is build-time enforcement (architecture-rule reachability, the same mechanism that forces mutating controllers to reach the audit writer) once real callers exist — not a two-entry protocol, which doubles chain traffic without closing the caller-never-writes hole.

**Redaction.** Rendered prompt, raw output, verdict, and edited output pass through the platform's existing redactor before hashing and persisting. "Raw" means verbatim modulo platform redaction; the stored form and hashed form are identical, so verification needs nothing new.

**Replay.** The Rendered prompt is inline (the *what*); retrieved-context refs plus content hashes give provenance (the *where-from*); the Prompt template is pinned by id + version + hash and lives in git. Refs are detection, not recovery — this resolved a contradiction in the original brief between "from the record alone" and "refs".

**Ruling governance and the gate.** Latest Disposition wins; earlier rulings remain in the record but no longer govern. The Actionable output under an accept is the Raw output; under an edit it is the stored full edited output served verbatim (the diff is derived at read time from the two immutable outputs — never stored, never applied, so no diff-application logic exists to get wrong); under reject, or with no ruling, the gate refuses. The gate lives at the MDR's read surface — the only enforcement site that exists until consumers appear.

**API contract.** No public OpenAPI change — the contract explicitly excludes internal routes and this increment has no customer-facing surface. Endpoints: `POST /api/v1/internal/mdr/decisions` (service JWT, internal) appends a Model Decision; `POST /api/v1/admin/mdr/decisions/{decisionId}/dispositions` (admin role) appends a Disposition; `GET /api/v1/admin/mdr/decisions/{decisionId}` serves the full Entry thread (the replay read, including derived diffs); `GET /api/v1/admin/mdr/decisions/{decisionId}/actionable-output` is the gate (409 until a qualifying ruling); `GET /api/v1/admin/mdr/chain-verification` verifies scope `mdr`.

**Idempotency.** Client-supplied entry id as natural idempotency: a replayed append hits the primary key and returns the existing Entry. This is a deliberate, argued deviation from the platform's `Idempotency-Key` header convention — that package has an open critique barring new uses, and a chain append has intrinsic identity that makes a response-snapshot mechanism redundant.

**Audit double-entry.** MDR mutating endpoints also record ordinary audit events, satisfying the mutating-controllers-reach-audit rule rather than seeking exemption. Audit chain = who did what; MDR chain = what was decided.

**Persistence.** One new migration: the entries table, CHECKs, indexes (unique seq; decision + seq; correlation + ts; parent link), append-only grants copied from the audit pattern, and the `mdr` genesis head row. Deliberate deviation: no time-range partitioning — audit's partitions exist for platform-wide write volume and carry an unbuilt partition-management job; MDR volume is demo-scale, and a plain table keeps a real primary key for the self-reference. Revisit alongside audit's multi-shard work.

**Synthetic-data boundary.** Satisfied architecturally, like every other table — investigation proved no per-row synthetic marker exists anywhere in the platform. A platform-wide marker, if ever wanted, is its own increment.

**No-op decider.** No production decider interface this increment — a seam with only a test implementation is dead production code. The no-op decider is test support (the established test-implementation-of-a-production-surface pattern), driving the internal write endpoint deterministically with controllable Outcomes including timeouts.

**Delivery order.** Four PRs: (1) chain-core extraction with byte-identical audit hashes proven by the existing harness; (2) migration + module + writer + internal write endpoint; (3) dispositions, gate, replay read, verification endpoint; (4) e2e flow + diagram lane wiring.

## Testing Decisions

A good test here asserts external behaviour only: the HTTP contract (strict-match JSON with no extra properties), the full database entity (every column, so any new field forces the test to change), and observable chain facts (hashes, sequence numbers, verification results) — never writer internals. The Kensa given/whenever/then style is used throughout; project conventions override generic TDD-skill examples.

All modules are tested (full convention coverage — client-confirmed):

- **Chain core**: isolation tests proving two scopes advance and verify independently without interference; the existing audit writer/verifier/canonical-JSON/redactor tests are the regression net and must pass untouched — they pin the audit chain's hashes byte-for-byte across the extraction.
- **mdr-service acceptance** (happy/sad per the architecture-test conventions): record COMPLETED / TIMED_OUT / ERRORED decisions; append accept / edit / reject Dispositions with hash-linkage assertions; latest-wins gate behaviour including refusal and re-opening; verbatim edited-output serving with derived diff in the replay read; tamper-a-hash-then-verify (mirroring the audit tamper test); scope isolation both directions; independent-write survival of business rollback; idempotent replay of a repeated entry id; validation, authorisation, and unknown-reference sad paths; audit events asserted on every mutation.
- **Repository**: tests for every custom finder, per the repository-test rule.
- **Architecture**: a full architecture test for the module — deliberately closing the gap some existing services left open.
- **CLI/job**: verification exits clean on intact chains and non-zero when either scope is tampered.
- **e2e**: the named flow — write a Model Decision as the no-op decider, attach a Human Disposition, verify the chain intact — recorded as a sequence diagram with the MDR on its own lane.

Prior art: the audit chain's writer/verifier/tamper tests, the ledger service's acceptance tests for full-entity database assertions, the payments e2e test for the flow shape, and the test-PAN-generator trio for the no-op decider pattern.

## Out of Scope

- Any LLM, provider SDK, or model integration; any production decider seam.
- Consumers of Actionable output, and the architecture rule forcing them through the gate (needs consumers to exist).
- A platform-wide synthetic-data marker.
- External chain anchoring (KMS-signed head, object-lock storage) — inherits the audit plane's documented tamper-detecting-not-tamper-evident limitation and its named future work.
- Partitioning, archival, and retention for MDR entries; the audit partition-management job gap.
- A back-office UI in the webapp; any public OpenAPI change.
- An MDR client-module pair.
- Multi-shard chains beyond the two scopes.

## Further Notes

The grilled design plan (`plan.md`, same directory) carries the full schema sketch, module layout, migration content, and PR sequence, and stays subordinate to this PRD where they differ. The glossary in the service's `CONTEXT.md` is binding vocabulary: Entry, Model Decision, Human Disposition, Decision, Outcome, Ruling in force, Actionable output, Rendered prompt, Prompt template, Raw output, Correlation ref, Chain. Three ambiguities in the original brief were resolved during grilling and are recorded there: the "hash-chained ledger" conflation, the replay-vs-refs contradiction, and the nonexistent synthetic marker.
