# PRD — Copilot: a cited, read-only explanation of why something happened

Status: ready-for-agent

Grilled 2026-07-22 from `client-brief.md`. Decisions recorded in ADR-0012 (the Copilot is a structurally non-authoritative read) and ADR-0013 (MDR Entries declare whether a ruling is expected); language in `malai-services/copilot-service/CONTEXT.md` and the new **Advisory** term in `malai-services/mdr-service/CONTEXT.md`.

## Problem Statement

An investigator looking at a referred payment can see *what* the platform recorded — a decision row, a payment state, a chain of audit events — but reconstructing *why it happened* means reading five surfaces and holding the sequence in their head. Meanwhile the MDR's only Decision point decides; nothing yet demonstrates that the same recorded, replayable, tamper-evident treatment applies to a model that merely *explains*. A bank evaluator's instinct about AI in a back office is that explanations are where the fabrication happens, and the platform currently has no answer to that instinct beyond wording.

## Solution

The Copilot is a read-only investigation aid on the Ops surface. Given a payment, it assembles a **Timeline** of records that genuinely exist — the payment row, its audit events, its ledger entries and hold, the standing order or mandate that spawned it, the screening decision and any ruling — invokes a model through a seam, and returns an ordered set of **Claim**s, each citing the **Source record**s it rests on. Grounding is machine-checked, not requested: a Claim citing anything outside the Timeline fails the whole answer, which is then recorded and refused rather than rendered. Every invocation — grounded, ungrounded, timed out or errored — appends exactly one Model Decision to the MDR chain, replayable 18 months later from the record alone.

Nothing may act on an Explanation, and that is enforced by the module graph rather than by policy: no `malai-copilot-client` exists, so no service can call it (ADR-0012). Copilot decisions declare themselves **Advisory** so the reviewer's worklist stays the list of things that hold money up (ADR-0013).

## Corrections to the brief

The brief was written before payment screening landed, and three of its premises are false on the ground:

- **"The FIRST model consumer that writes onto the MDR"** — payment screening already is, live, with a decider seam, a hashed prompt template, real context refs and shadow/enforce modes (`.scratch/payment-screening/`). The Copilot is the second Decision point and the first *read-only* one. The model-invocation seam and MDR write path are therefore *conformance to an existing pattern*, not new architecture.
- **"Grounding is ledger entries, scheduler runs, CoP verdicts, forecast records"** — only ledger entries exist as records. A scheduler firing leaves a payment and audit events, not a run row; `ConfirmationOfPayeeService` computes a verdict and discards it; `BalanceForecastAggregator` derives a forecast per request. The Copilot cites what is genuinely recorded and explains a short forecast from the standing orders and mandates whose occurrences fall in the window.
- **"Carries the synthetic marker like everything else"** — no such marker exists anywhere in the platform; the synthetic boundary is architectural (no real rails, fictional counterparties), exactly as the MDR's CONTEXT.md already resolved.

## User Stories

1. As an ops reviewer holding a referred payment, I want a cited account of what happened to it, so that I can rule without assembling the sequence by hand across five surfaces.
2. As an ops reviewer, I want every sentence to carry the id of the record it came from, so that I can check any claim against the source in one click.
3. As an ops reviewer, I want an answer that cites a record the platform never retrieved to be refused outright, so that I never read a fabricated citation dressed as a fact.
4. As a compliance officer, I want the refusal itself recorded with the raw output intact, so that "the model produced something we would not show" is provable rather than asserted.
5. As a compliance officer, I want every explanation to be a chained MDR Entry with model id, template hash, retrieved-record ids, latency and cost, so that it replays in eighteen months from the record alone.
6. As a compliance officer, I want a timeout or error to append its own Entry, so that a missing answer is a recorded fact and not an absence.
7. As a bank architect, I want it to be structurally impossible for any service to consume an explanation, so that a read-only aid cannot quietly become an input to a decision.
8. As an ops reviewer, I want read-only explanations kept out of my worklist, so that the list of things awaiting a ruling stays the list of things holding money up.
9. As a demo presenter, I want one armed subject that makes the model time out and one that makes it fabricate a citation, so that I can show both failure modes on cue without a real provider.
10. As a platform maintainer, I want the decider behind a seam with a deterministic default, so that wiring a real provider changes an implementation, not an architecture.
11. As a real-bank deployer, I want to read exactly what would leave my estate when a provider is wired in, so that I can assess the model-invocation boundary from the stored Rendered prompt.
12. As an ops reviewer, I want a repeated request to return the same explanation, so that a double-click neither forks the record nor spends twice.

## Implementation Decisions

- **Placement**: a new `copilot-service` bounded context under `malai-services/`, shaped like `exec-service` (ADR-0011) — owns no tables and no money state, composed over the contexts that own the facts through their clients, with a service-token client of its own.
- **Subject**: a `(type, id)` ref matching the MDR's Correlation ref shape; v1 resolves `PAYMENT` only. `ACCOUNT` is additive later (one more resolver, no contract change). No Case exists.
- **Corpus**: only what is genuinely recorded — audit events, payment rows, ledger entries/transactions/holds, standing orders and mandates, MDR entries. Nothing derived is cited as a source.
- **Invocation**: one fixed "explain this payment" — a single versioned `copilot-payment/v1` template rendered from the Timeline. No free text reaches the model in v1, so prompt injection has a structural answer and replay is exact.
- **Grounding**: the decider returns an ordered list of Claims, each with text and `(type, id)` source refs; the service validates every ref against the exact assembled Timeline. All-or-nothing (ADR-0012).
- **Recording**: exactly one Model Decision per invocation, appended *before* the response is served. `advisory = true`. Retrieved-context refs are one per Source record in view, each with a content hash over the projection actually used — the ref list *is* the citation whitelist, so grounding and provenance are one artifact.
- **Outcomes**: COMPLETED with Verdict = the grounded Claims; COMPLETED with Verdict absent when grounding fails (raw output stored); TIMED_OUT and ERRORED as screening already defines them. An MDR write failure fails the request (fail-closed).
- **Timing**: synchronous, record-before-serve, property-tunable timeout budget. Async is the named upgrade path if a real provider blows the budget.
- **Redaction**: screening's masking (account numbers and IBANs to last four) with balances and holds deliberately *included* — the available balance at the moment of the attempt is frequently the whole answer, and the Copilot explains rather than decides.
- **Authorization**: ADMIN only, like every `/api/v1/admin/mdr/*` route; a MANAGER JWT receives 403, which is a second demonstration of ADR-0010's boundary.
- **Idempotency**: `POST /api/v1/admin/copilot/explanations` is registered as an idempotency-protected route — it appends to a hash chain and, behind a real provider, spends money.
- **Non-authoritative**: no `malai-copilot-client` module is created, deliberately, and the copilot ArchUnit test records that absence as an architectural fact (ADR-0012).
- **Worklist**: the MDR write request gains `advisory`; the summary row carries it and the worklist filters on it, with the reviewer's default view narrowing to non-advisory awaiting-ruling decisions (ADR-0013).
- **Prerequisite reads**: `GET /api/v1/internal/payments/{paymentId}` and `GET /api/v1/internal/ledger/transactions/{transactionId}/entries` (ledger-service already has the finder internally, unexposed), plus an `AuditReader` beside `AuditWriter` in `malai-commons/malai-security` — audit is shared infrastructure with no owning context, so a shared reader is symmetric with how every service already writes.
- **Decider**: `CopilotDecider` seam, deterministic implementation named honestly (`malai-copilot-template` v1) as the default and the only one tests and e2e touch. Two armed subjects — one times out, one fabricates a citation. A real provider adapter is the last issue, property-gated and default off.
- **Configuration blast radius**: the new service's `malai.*.base-url` properties must be declared in copilot-service and the aggregator app across main, test and acceptance-boot configuration — the known new-client gotcha. Copilot acceptance diagrams gain WireMock lanes for payments, ledger and MDR.

## Testing Decisions

- A good test asserts external behaviour — response payloads, MDR entries recorded, HTTP interactions on the diagram, full-entity database assertions — never Timeline-assembly internals or decider internals.
- **Copilot acceptance (Kensa, happy/sad)**: an explanation over a settled FPS payment with the recorded MDR request body pinned; every claim's citations resolving into the retrieved-context ref list; the armed timeout and armed ungrounded subjects; a decider error; an MDR-write failure failing the request; unknown payment 404; MANAGER 403; missing Idempotency-Key rejected; a replayed key returning the first explanation.
- **Grounding validator direct tests**: the volatile rule deserves fast feedback — claim citing outside the Timeline, claim citing nothing, empty claim list, duplicate citations.
- **Client pair tests**: WireMock-backed request-construction and deserialisation for the two new internal reads, per the client-module convention.
- **Repository/reader tests**: `AuditReader`'s finder, per the house rule that custom finders are tested.
- **MDR acceptance**: `advisory` on write, on the summary row, and as a worklist filter; absent means not advisory for seeded rows.
- **e2e**: one journey — explanation requested for a synthetic payment, Entry appended and chained, chain verification walks it, claims cite ids that resolve.
- **Webapp** (tracked in `malai-webapp/.scratch/copilot/`): Vitest for the citation rendering and the grounding-refused state; one Playwright spec over the rendered explanation.

## Out of Scope

- `ACCOUNT` and any other Subject type; free-text questions; explanation history by subject (the MDR's `(correlation_type, correlation_id, ts)` index already makes it cheap when wanted).
- Document/policy RAG in any form.
- Any human disposition flow *for* explanations beyond what the MDR already allows, and any case-management linkage.
- Persisting scheduler runs, CoP verdicts or forecast snapshots — named as the honest way to make those citable later, not built here.
- Any write-back to ledger, accounts or payments; auto-dispositioning; any consumer of an Explanation.
- Seeded copilot demo entries — explanations are produced live on demand.

## Flagged: not testable against synthetic data alone

- **Issue 08 (real provider adapter)** — needs a live provider and a key in `malai-infra`. Everything else in this feature runs green against synthetic data with no external dependency, and the deterministic decider remains the default in every test and e2e path.
