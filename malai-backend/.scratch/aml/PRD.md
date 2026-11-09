# PRD — AML: Post-Event Monitoring, Cases, and the Drafted SAR

Status: ready-for-agent

Grilled 2026-07-24; decisions recorded in `.scratch/aml/exploration.md`. Language follows the MDR glossary (Decision point, Model Decision, Human Disposition, Outcome, Correlation ref, Gate, Ruling in force, Advisory) and the payments glossary (Screening, Referral, Lifecycle). Binding ADRs: un-ruled model verdicts move the bank conservative-only; model output becomes real only through the Gate; composed reads own no domain they merely read; Copilot stays structurally non-authoritative.

## Problem Statement

The platform can show a payment being judged — one payment, before money moves. It cannot show what every real bank's financial-crime team actually spends its days on: patterns. Five sub-threshold payments that are one £10k transfer wearing a disguise sail through screening untouched, because the screening decider was deliberately built with no balances and no history. There is no transaction monitoring, no alert, no case, no investigation workflow, and no Suspicious Activity Report — the words a bank evaluator's compliance colleagues will ask about first. The MDR→Screening→Copilot arc demonstrates recorded decisions, enforced rulings, and grounded explanations, but the story stops at the individual payment; the platform has no answer to "and what happens after the money moves?"

## Solution

AML becomes the MDR's first post-event Decision point family. A monitoring sweep re-evaluates rolling windows of settled payments every few seconds against deterministic, honestly-recorded typology rules — structuring, rapid in-out, high-risk corridor. Each pattern found is an alert: one non-advisory Model Decision correlated to the account, its evidence payments cited as content-hashed context refs, deduplicated so a pattern seen twice raises once. An alert auto-opens (or joins) the account's single open case, and the case worklist on the Ops surface becomes the investigation home: the officer rules on each alert through the MDR, requests an AI-drafted SAR narrative — grounded in a cited timeline, recorded as its own Model Decision — and closes the case. Closing as reported reads the SAR ruling through the Gate at that moment: an accept or edit in force attaches the narrative and lands `CLOSED_REPORTED`; anything else is a refusal that leaves the case parked. The customer's app never changes by one pixel — the tipping-off invariant is itself part of the demo. Screening stops the £10k payment; AML catches it split into five £2k payments; and the tamper-evident chain holds the alert, the rulings, and the accepted SAR narrative end to end.

## User Stories

1. As a bank evaluator, I want five sub-threshold payments to the same beneficiary to raise a structuring alert, so that I see the platform catch what per-payment screening deliberately cannot.
2. As a bank evaluator, I want an inbound credit that immediately drains outbound to raise a rapid in-out alert, so that mule-account behaviour is visibly detected.
3. As a bank evaluator, I want a SWIFT payment to a high-risk jurisdiction to raise a corridor alert, so that cross-border risk is visibly monitored.
4. As a demo presenter, I want detection to land within seconds of the last payment in the pattern, so that I can fire a typology live on stage and watch the worklist react.
5. As a demo presenter, I want the typology rules to be deterministic triggers, so that the beat lands on cue without any hidden armed mechanism.
6. As an ops reviewer, I want each alert to be one Model Decision for one (account, typology, evidence set), so that I rule on a specific pattern, not a bundle.
7. As an ops reviewer, I want the alert's verdict to carry the typology, a risk score, and the evidence payments as content-hashed context refs, so that replay reconstructs exactly which payments fired the rule and proves they haven't been doctored.
8. As an ops reviewer, I want a pattern seen by overlapping sweep windows to raise exactly one alert, so that the worklist reflects patterns, not polling cadence.
9. As a compliance officer, I want quiet sweeps to record nothing, so that the decision record stays a record of decisions and not a heartbeat log.
10. As a compliance officer, I want a decider failure while evaluating a candidate pattern recorded as its own Outcome, so that a missing answer is a fact on the chain, not a silent gap.
11. As a compliance officer, I want the record to name the rules model that actually ran, so that the audit plane never claims a model invocation that didn't happen.
12. As a compliance officer, I want the high-risk jurisdiction list versioned and pinned by content hash in every corridor alert, so that I can prove later which list version fired.
13. As an ops reviewer, I want an alert to auto-open a case for its account, or join the account's already-open case, so that investigation state has one home per customer.
14. As an ops reviewer, I want a case worklist on the Ops surface, so that open investigations are findable without querying the MDR by hand.
15. As an ops reviewer, I want the case detail to assemble the alerts, their evidence payments, their dispositions, and the SAR state, so that one screen tells the investigation's whole story.
16. As an ops reviewer, I want alerts to appear on the MDR worklist like screening decisions do, so that the model-governance view stays complete while I work from the case view.
17. As an ops reviewer, I want a disposition made from the case surface to clear the item from both worklists, so that the two views never disagree about what awaits ruling.
18. As an ops reviewer, I want to reject a false-positive alert and see the case closeable as dismissed once no alert survives, so that noise leaves the system through the same ruled front door.
19. As an ops reviewer, I want closing as dismissed to be refused while an accepted alert stands, so that a case with confirmed suspicion cannot be quietly waved away.
20. As an ops reviewer, I want to request an AI-drafted SAR narrative for a case, so that the writing-up drudgery is drafted for me while the judgment stays mine.
21. As an ops reviewer, I want every factual claim in the draft to cite records from the assembled case timeline, so that I can verify the narrative against the evidence before signing it.
22. As a compliance officer, I want a draft whose citations stray outside the timeline to be refused and recorded as a failure, so that an ungrounded narrative can never reach a filing.
23. As an ops reviewer, I want to accept the draft, or edit it and have the diff kept, so that the filed narrative is mine and the record shows what the model contributed.
24. As an ops reviewer, I want closing as reported to consult the Gate at that moment — accept or edit in force attaches the narrative and closes; anything else refuses and the case stays parked, so that model output becomes real only through a ruling.
25. As an ops reviewer, I want a retried close to enact exactly once, so that a flaky network can neither fork the record nor double-close a case.
26. As a compliance officer, I want case transitions on the audit chain and alert and SAR entries on the MDR chain, so that tamper-evidence covers the investigation end to end and chain verification walks it all.
27. As a retail customer under investigation, I want my app, my payments, and my error messages identical to everyone else's, so that the bank never tips me off — and the evaluator sees the platform knows this rule.
28. As a bank evaluator exploring alone, I want a seeded closed case with its accepted SAR and a seeded open case mid-flight, so that the AML surface tells its story without me reproducing a typology first.
29. As a bank executive watching a demo, I want the live-fired pattern, the alert, the ruling, and the SAR close to happen in one sitting, so that the full loop is something I watched, not something I was told about.
30. As a real-bank deployer, I want both deciders behind seams with deterministic defaults and the real provider adapter off by default, so that adopting the platform never opts me into a model vendor I didn't configure.
31. As a platform maintainer, I want monitoring to poll the payments read surface rather than reach into other services' tables, so that context boundaries hold and an event-driven upgrade stays a swap, not a rescue.
32. As a platform maintainer, I want the typology engine testable in isolation from the sweep, the MDR, and the database, so that detection logic evolves under fast tests.

## Implementation Decisions

**New bounded context: `aml-service`.** Owns post-event monitoring, alerts-as-decisions, cases, and SAR drafting. Ships with its own CONTEXT.md (Alert, Case, Typology, Evidence set, Sweep, SAR draft, Tipping-off) and a CONTEXT-MAP entry. No client module pair — nothing calls AML in this increment (the mdr-service precedent). A new ADR records the generalisation of the Gate rule: an AML case's terminal transitions are Gate-governed, extending "referred payments move only via the Gate" beyond payments.

**Detection is a stateless rolling-window sweep.** A ShedLock-coordinated scheduled job (~10s) re-evaluates rolling windows ending now over settled payments. No watermark, no high-water-mark table: evidence-set-hash dedup makes re-evaluation idempotent, and it dissolves the created-before/settled-after gap that a timestamp watermark would leak through. Sweep cadence and all typology parameters are configuration under a single `malai.aml.*` prefix.

**Facts come from payments, over HTTP.** A new internal service-JWT read endpoint on payments-service returns settled payments in a window; the payments client gains the matching method and test-support builders. Internal routes stay out of the OpenAPI contract by its own stated rule; the admin AML endpoints go into the contract first, controllers after. The payments table needs no schema change: beneficiary identity is sort code + account number (IBAN for SWIFT), in/out correlation uses the existing account+created-at indexes, and jurisdiction derives from IBAN characters 1–2 or BIC characters 5–6.

**Three typologies, one deep module.** The `AmlMonitoringDecider` seam fronts a deterministic `RulesAmlDecider` — model id `malai-aml-rules` version 1, rendered prompt template `aml-monitoring` v1, screening's honesty posture throughout (the rules are recorded as a model invocation truthfully named, so a real model swaps in without architectural change). Inside it: structuring (N outbound payments, same beneficiary, individually under the screening threshold, jointly at or above it, within a rolling window), rapid in-out (inbound credit followed by outbound transfers moving at least a configured percentage within a short window), high-risk corridor (SWIFT beneficiary jurisdiction on a versioned fictional list, config-with-version exactly like the screening watchlist). The decider is a pure function from a window of payment facts to typology hits with evidence — testable with no Spring, no database, no HTTP.

**An alert is its Model Decision; there is no alert table.** One non-advisory `MODEL_DECISION` per (account, typology, evidence set), correlation ref ACCOUNT/(uid), verdict carrying typology, risk score, and evidence refs (payment ids with content hashes — the citation-whitelist pattern). Dedup keys on the hash of (typology + sorted evidence payment ids). Quiet sweeps and dedup-suppressed re-detections record nothing; a decider failure over a candidate pattern records `ERRORED`/`TIMED_OUT`.

**One new table: `aml_cases` (migration V26).** Case uid, subject account, status machine `OPEN → INVESTIGATING → CLOSED_DISMISSED | CLOSED_REPORTED`, linked alert decision ids and the SAR decision id as opaque refs (never foreign keys into MDR entries), timestamps. One open case per account: an alert opens a case or attaches to the existing open one, atomically with its recording. Case transitions emit audit events (`AML_CASE_OPENED`, `AML_CASE_CLOSED_DISMISSED`, `AML_CASE_CLOSED_REPORTED`) joining the existing action-type family.

**Alerts and SAR drafts are non-advisory.** Both demand human rulings — the definition of non-advisory. They appear on the MDR worklist (model-governance view) while the case worklist is the investigation view of the same facts; a Human Disposition recorded through the existing MDR admin surface clears both. Copilot remains the platform's only advisory citizen.

**The SAR draft is a grounded Model Decision.** On request for a case, a timeline assembler composes the cited record set (evidence payments, alert decisions and their dispositions, case audit events — each ref content-hashed) and the `SarDraftDecider` seam produces the narrative: deterministic template drafter as default in every profile (model id honestly named, version 1, prompt template `sar-narrative` v1), a real Anthropic adapter property-gated off, budget-bounded, following the Copilot adapter's shape. Grounding is machine-checked and all-or-nothing: a narrative citing outside the timeline is recorded with raw output kept and verdict absent, then refused. Correlation ref CASE/(uid). Copilot itself is untouched and remains impossible to consume programmatically.

**Terminal case transitions consult the Gate synchronously.** Close-as-reported reads the SAR decision's actionable output at the moment of the officer's action: a ruling in force yields the (possibly edited) narrative, which is attached as the case closes `CLOSED_REPORTED`; no ruling or a reject is a domain-coded 409 refusal and the case stays `INVESTIGATING` — conservative-only, no second resolver sweep, because a human is already driving. Close-as-dismissed is refused while any linked alert has an accept ruling in force. Both close actions are idempotency-protected.

**Admin API, contract-first.** Case worklist, case detail, request-SAR-draft, close-as-dismissed, close-as-reported — ADMIN role, added to the OpenAPI contract before any controller exists. Alert dispositions need no new endpoint: the existing MDR disposition surface is the door, and case detail exposes decision ids so the Ops surface can drive it.

**The tipping-off invariant is architectural.** No customer-facing surface may reveal AML state: no feed item, no payment-state difference, no error-message difference, and no customer-path module may depend on aml-service — enforced by architecture rules, not convention.

**Demo state: seeded and live.** The seeder builds one `CLOSED_REPORTED` case (alerts, dispositions, accepted SAR — the full arc visible to a self-guided evaluator) and one `OPEN` case mid-flight; the demo script fires the structuring pattern live from a seeded persona and the alert lands within a sweep tick.

## Testing Decisions

A good test exercises external behaviour — the HTTP surface, the recorded MDR entry, the database row, the refused close — never the internals that produced them. Acceptance tests assert full response payloads with strict no-extra-props matching and full-entity database assertions, per house style.

- **`RulesAmlDecider`** — the deep module — gets exhaustive isolated tests: each typology's boundary conditions (N−1 payments, threshold-exact sums, window-edge timing, percentage-exact drains, jurisdiction derivation from IBAN and BIC), no Spring context. Prior art: the screening rules decider's isolated tests.
- **Sweep + alert recording** — Kensa acceptance tests on the aml-service surface: pattern in → alert decision recorded with correct verdict, evidence refs, and dedup on re-sweep; quiet window records nothing; decider failure records its Outcome. Prior art: screening's happy/sad acceptance pairs and the referred-payment resolver tests.
- **Case lifecycle** — acceptance tests for auto-open/attach, one-open-case-per-account, worklist and detail payloads, close-as-dismissed refusal while an accepted alert stands, audit events asserted as full entities.
- **SAR slice** — acceptance tests for draft recording, grounding refusal as a recorded failure, close-as-reported through the Gate (accept, edit-with-diff, reject-refusal, no-ruling refusal), idempotent close. Prior art: Copilot's grounding and recorded-failure tests; the Gate tests in mdr-service.
- **Payments internal read + client** — WireMock-backed client tests (request construction and deserialisation) and an acceptance test on the new internal endpoint. Prior art: every existing client pair.
- **Tipping-off** — sad-path acceptance tests asserting customer endpoints respond identically for monitored and unmonitored accounts, plus an architecture rule test that no customer-path module depends on aml-service.
- **Repository tests** for any custom case-repository queries, per house rule.
- **End-to-end** — the full arc against the running stack with its sequence-diagram lane: live-fired structuring → alert → dispositions → SAR draft → gate close → chain verification passing over both chains.

## Out of Scope

- Customer risk rating, CDD/EDD, periodic KYC review.
- Account restriction or freezing (named future Decision point — a second Gate consumer when it comes).
- Inbound-payment and scheduled-payment screening (already-named future Decision points of the screening feature).
- Card-spend typologies (separate rail path through card-service).
- Real sanctions/PEP data, real jurisdiction lists, real NCA filing — `CLOSED_REPORTED` is the terminal demo state; no submission stub.
- Event-driven detection (SQS remains the rail bus; the polling sweep names event-driven as its production upgrade path, as screening did for dispositions).
- Exec-dashboard metrics (an AML cases source joins the deferred fraud-prevented number in the exec-metrics backlog).

## Further Notes

- BankingDay is deliberately not used: detection windows are rolling durations, and AML v1 has no day-scoped figures.
- No `settled_at` column is added; the stateless sweep makes settlement-time bookkeeping unnecessary.
- The structuring typology deliberately references the screening threshold so the demo sentence composes: screening stops the £10k payment, AML catches it split into five.
- Jurisdiction derivation from IBAN/BIC rather than a stored country column mirrors real transaction-monitoring practice and needs no payments schema change.
- The suggested nine-issue arc (contract + bootstrap → payments read → structuring tracer → remaining typologies → dispositions and dismiss path → SAR slice → e2e + diagram lane + tipping-off tests → seeded demo state → gated provider adapter) is recorded in `.scratch/aml/exploration.md`.
