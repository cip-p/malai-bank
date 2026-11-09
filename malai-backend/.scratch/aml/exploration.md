# AML — exploration

Status: ready-for-human
Grilled: 2026-07-24 — decisions below supersede the original proposal text where they differ.

## Brief

client-brief.md: "I want to tackle the next important feature for our digital banking, AML. MDR and Ledger Copilot was just implemented, please explore the addition of AML."

## Where AML lands in the existing story

The financial-crime arc so far:

1. **MDR** — append-only, hash-chained record of model decisions, with human dispositions and the actionable-output gate (ADR-0008, ADR-0009).
2. **Payment screening** — the MDR's first live Decision point: synchronous, pre-dispatch, per-payment rules (amount threshold + fictional watchlist), SHADOW→ENFORCE, `REFERRED` parking, resolver sweep enacting rulings.
3. **Ledger Copilot** — grounded, citation-checked, structurally non-authoritative explanations, every invocation an advisory MDR decision (ADR-0012, ADR-0013).

AML is the fourth chapter, and it is precisely what screening declared out of scope. The screening PRD is explicit: the decider sees **"no balances and no history."** Screening judges one payment before money moves. AML judges **patterns across payments, per account, over time, after money moves** — and adds the case workflow that ADR-0012 already anticipated ("compatible with the case-management disposition flow arriving next").

The demo sentence AML buys: *"Screening stops the £10k payment; AML catches it when it's split into five £2k payments — and here is the tamper-evident record of the alert, the case, and the AI-drafted SAR narrative the officer accepted."*

## Decisions (grilled)

1. **v1 scope: the full arc** — detection → alert → case → SAR narrative draft ruled through the gate. The SAR slice is the AI showpiece; without it AML is another rules sweep.
2. **Alert grain: one MDR Model Decision per (account, typology, evidence set).** The officer rules on a specific pattern; verdict schema stays one-typology-shaped; dedup keys on the evidence-set hash.
3. **Alerts auto-open/attach to a case; one open case per account.** No separate alert-triage worklist. Dismissing a false positive = REJECT disposition on the alert; the officer closes the case `CLOSED_DISMISSED` when no alert on it survives.
4. **Sweep model: stateless rolling windows, no watermark.** A ShedLock `@Scheduled` job (~10s, `ReferredPaymentResolver` pattern) re-evaluates rolling windows ending now over **settled** payments. Evidence-set-hash dedup makes re-evaluation idempotent. This dissolves the missing-`settled_at` problem (a created-before/settled-after payment is just picked up by a later evaluation) and needs no watermark table. *Correction:* `BankingDay` is **not** used — detection windows are rolling durations; `BankingDay` is for day-scoped figures, which AML v1 has none of.
5. **Quiet sweeps record nothing.** The sweep is not a Decision point — each raised alert is. Recorded-absence still holds where it matters: a decider failure while evaluating a candidate pattern records an `ERRORED`/`TIMED_OUT` decision, per screening's outcome machinery. Dedup-suppressed re-detections also record nothing.
6. **No account actions in v1.** Observe → investigate → report; the account keeps operating (realistic pre-SAR posture given tipping-off risk). Account restriction is named in the PRD as a future Decision point, alongside inbound screening.
7. **Alerts and SAR drafts are non-advisory** (*correction* to the original proposal, which said `advisory = true`). Both demand human rulings — the definition of non-advisory under ADR-0013. They appear on the MDR worklist like screening decisions; the case worklist is the investigation view of the same facts; a disposition made from the case surface clears both. Copilot remains the only advisory citizen.
8. **SAR accept → close is synchronous.** The officer's "close as reported" action calls the gate's `actionable-output` for the SAR decision at that moment: ruling in force → attach narrative, transition `CLOSED_REPORTED`, audit event; no ruling / REJECT → domain-coded 409, case stays `INVESTIGATING` (ADR-0008 conservative-only). No second resolver sweep — a human is already driving.
9. **Demo choreography: seeded and live.** Seeder builds one `CLOSED_REPORTED` case (full arc visible: alerts, dispositions, accepted SAR) and one `OPEN` case mid-flight. The live beat: fire the structuring pattern (five sub-threshold payments from a seeded persona) and the alert lands on the worklist within a sweep tick. No new "armed" mechanism — the typology rules are themselves deterministic triggers.
10. **Exec metrics deferred** to the exec-metrics backlog, joining the already-deferred fraud-prevented number; a follow-up increment adds an `AmlCasesSource` to the fan-out.

## Resolved by the codebase (no user decision needed)

- **Structuring feasibility** — "same beneficiary" = `beneficiary_sort_code`+`beneficiary_account_no` (or `beneficiary_iban` for SWIFT); `payments` already has beneficiary/debtor account+`created_at` indexes (V15).
- **Rapid in-out feasibility** — inbound rows carry `beneficiary_account_id`, outbound rows `debtor_account_id`; per-account correlation works off existing indexes.
- **Jurisdiction** — no country column and none needed: derive from `beneficiary_iban` chars 1–2 or `beneficiary_bic` chars 5–6 (how real TM systems do it). Fictional high-risk list is versioned config like `ScreeningWatchlist`.
- **`BankingDay`** exists (`malai-commons/.../time/BankingDay.kt`) but is not needed here (decision 4).
- **Card spend** is a separate rail path through `card-service` — out of v1 typology scope.

## What exists to build on (verified)

- **MDR correlation refs are domain-agnostic** `(type, id)` — alerts correlate to `ACCOUNT`, SAR drafts to `CASE`, no schema change. Verdict JSON is template-defined.
- **Decider seam pattern** — `ScreeningDecider`/`CopilotDecider`: property-selected implementation, deterministic default in all test profiles, real provider adapter gated off. AML gets `AmlMonitoringDecider` and `SarDraftDecider` on the same shape.
- **Sweep pattern** — `payments-service/scheduler/ReferredPaymentResolver.kt` (ShedLock `@Scheduled`).
- **No event stream exists to subscribe to.** SQS is a rail-integration bus only; `ledger-service` emits nothing; read models pull over internal HTTP (ADR-0011). Hence the polling sweep.
- **Fact source**: the `payments` table alone feeds all three v1 typologies, via a new internal read endpoint on payments-service (settled payments in window), consumed through `malai-payments-client`. `AuditReader` available if lifecycle history is ever needed.
- **Internal endpoint auth pattern**: `@InternalEndpoint` + service-JWT scope, as in `InternalPaymentsMetricsController`.

## Proposed shape (v1)

New module: `malai-services/aml-service`. No `malai-aml-client` pair — nothing calls AML in the base increment (mdr-service precedent).

### 1. Monitoring sweep (detection)

- ShedLock `@Scheduled` (~10s), stateless rolling-window evaluation per decision 4.
- Reads settled payments in window via new internal payments-service endpoint (e.g. `GET /api/v1/internal/payments/settled?from=...&to=...`).
- `AmlMonitoringDecider` seam; production impl `RulesAmlDecider`, model id `malai-aml-rules` v1; rendered prompt template `aml-monitoring` v1 (screening's honesty posture — rules recorded as if a model, so a real model swaps in later).
- Every alert = one non-advisory MDR `MODEL_DECISION`, correlation `ACCOUNT`/(uid), verdict carrying typology, risk score, and content-hashed evidence refs (payment ids — the citation-whitelist pattern).
- Dedup: evidence-set hash (typology + sorted payment ids); a pattern seen twice raises once.

### 2. v1 typologies (deterministic, seedable, demoable)

1. **Structuring** — N outbound payments to the same beneficiary within a rolling window, individually under the screening threshold, jointly at/above it. Deliberately references the £10k screening threshold.
2. **Rapid in-out (pass-through)** — inbound credit followed by outbound transfers moving ≥X% of it within a short window.
3. **High-risk corridor** — SWIFT payment whose derived beneficiary jurisdiction is on the versioned fictional list.

Parameter defaults (N, windows, X%) are PRD material.

### 3. Cases

- Table `aml_cases`: case uid, subject account, status `OPEN → INVESTIGATING → CLOSED_DISMISSED | CLOSED_REPORTED`, linked alert decision-ids (opaque refs, never FKs into `mdr_entries`), SAR decision-id when one exists, timestamps. One open case per account (decision 3).
- Officer rulings on alerts are MDR `HUMAN_DISPOSITION`s. Case transitions are audit events (`AML_CASE_OPENED`, `AML_CASE_CLOSED_DISMISSED`, `AML_CASE_CLOSED_REPORTED`).
- Admin surface (ADMIN role, OpenAPI-first): case worklist + case detail (assembled view of alerts, evidence payments, dispositions, SAR state).

### 4. SAR narrative drafting

- On demand for a case: aml-service assembles a grounded timeline (Copilot's assembler pattern; sources fetched via clients, each ref content-hashed) and invokes `SarDraftDecider` — template `sar-narrative` v1, deterministic default, Anthropic adapter later.
- The draft is a non-advisory MDR `MODEL_DECISION`, correlation `CASE`/(uid). Officer ACCEPTs/EDITs via disposition; close-as-reported reads the ruling synchronously through the gate (decision 8). Copilot itself stays untouched and non-authoritative.
- No real filing. `CLOSED_REPORTED` is terminal; a stub NCA submission is out of scope.

### 5. Tipping-off invariant

No customer-facing surface may ever reveal AML state — no feed item, no payment-status difference, no error-message difference. Stated as a named invariant in the PRD; enforced by sad-path acceptance tests asserting customer endpoints respond identically for monitored and unmonitored accounts, plus the absence of any aml-service dependency in customer-path modules (ArchUnit).

## Explicitly out of scope (v1)

- Customer risk rating / CDD / EDD and periodic KYC review.
- Account restriction/freeze (named future Decision point — decision 6).
- Inbound and scheduled-payment *screening* (named future Decision points in the screening PRD — separate feature).
- Card-spend typologies (separate rail path).
- Real sanctions/PEP data, real jurisdiction lists, real filing.
- SQS/event-driven detection (production upgrade path note, as screening did for dispositions).
- Exec metrics tile (deferred — decision 10).

## Remaining PRD-level details (no grilling needed)

- Fictional jurisdiction list contents and naming (watchlist "Carol Singh" stance).
- Typology parameter defaults and their config property names (`malai.aml.*`).
- Seeded persona who performs the mule pattern; seeder wiring (mdr issue 09/10 precedent).
- SAR narrative template wording for the deterministic drafter.

## Suggested issue arc (mirrors mdr/copilot shaping)

01 contract + module bootstrap (openapi.yaml first, `aml_cases` migration, service skeleton, archunit test) →
02 payments internal settled-window read + client support →
03 tracer: structuring typology end-to-end (sweep → non-advisory alert decision in MDR → case auto-opened → worklist shows it) →
04 remaining typologies + jurisdiction list →
05 alert dispositions + case lifecycle (dismiss path) →
06 SAR draft slice (grounded assembly, `SarDraftDecider`, synchronous gate close → CLOSED_REPORTED) →
07 e2e + diagram lane + tipping-off invariant tests →
08 seeded AML demo state + live-fire demo script →
09 (gated) real provider adapter for SAR drafting.
