# AML

Post-event transaction monitoring and the investigation workflow it feeds: the Sweep that re-reads settled payments for patterns, the Alert each found pattern records, the Case that is an account's single investigation home, and the drafted Suspicious Activity Report that closes the arc through the Gate. Screening judges one payment before money moves; AML judges patterns across payments after it has.

## Language

**Alert**:
A pattern found — one non-advisory **Model Decision** recorded through the MDR client, Correlation ref `ACCOUNT`/(uid), its Verdict carrying the Typology, a risk score, and the Evidence set as content-hashed context refs. There is no alert table: the MDR Entry is the Alert.
_Avoid_: warning, hit, flag; Referral (the screening word — a Referral parks one payment pre-move, an Alert parks nothing).

**Case**:
The single investigation home for an account — the one `aml_cases` row that is `OPEN` or `INVESTIGATING` for it at any time, database-enforced. An Alert opens one or attaches to the open one, atomically with its recording. Status machine `OPEN → INVESTIGATING → CLOSED_DISMISSED | CLOSED_REPORTED`; it links its Alert decision ids and SAR decision id as opaque refs, never foreign keys into the MDR.
_Avoid_: investigation (the activity, not the record), ticket, file.

**Typology**:
A named laundering pattern the Decider can find — `STRUCTURING`, `RAPID_IN_OUT`, `HIGH_RISK_CORRIDOR`. Each Alert carries exactly one.
_Avoid_: rule (the implementation inside the Decider), scenario.

**Evidence set**:
The exact settled payments a Typology fired over, pinned in the Alert as content-hashed refs (the citation-whitelist pattern). Its hash — Typology plus sorted evidence payment ids — is the Alert's dedup identity: a pattern seen by overlapping Sweep windows raises once.
_Avoid_: proof, transaction list.

**Sweep**:
The ShedLock-coordinated scheduled job (~10s) that statelessly re-evaluates rolling windows ending now over settled payments, read through the payments client. Quiet Sweeps record nothing; a Decider failure over a candidate pattern records its own `ERRORED`/`TIMED_OUT` Outcome. No watermark — Evidence-set dedup makes re-evaluation idempotent.
_Avoid_: batch, poller, scan.

**SAR draft**:
A Suspicious Activity Report narrative drafted for a Case by the `SarDraftDecider` seam and recorded as a non-advisory **Model Decision**, Correlation ref `CASE`/(uid), grounded all-or-nothing against the Case's assembled timeline. It becomes the Case's filed narrative only when close-as-reported reads an accept or edit ruling through the **Gate** (ADR-0014). Two deciders sit behind the seam: the deterministic template drafter (the default in every profile, including test and e2e) and the `chat-completions` provider adapter, property-selected via `malai.aml.sar.decider` and speaking the OpenAI-compatible surface (local Ollama for smoke tests, any conforming endpoint in principle).
_Avoid_: report (the filed thing — v1 files nothing), summary.

**Tipping-off**:
The invariant that no customer-facing surface may reveal AML state — no feed item, no payment-state difference, no error-message difference, and no customer-path module depending on `aml-service`, architecture-enforced. Named for the UK offence it models.
_Avoid_: secrecy, stealth mode.

## Relationships

- An **Alert** is one **Model Decision** for one (account, **Typology**, **Evidence set**); the same pattern re-found is the same Alert, suppressed by dedup.
- An **Alert** opens the account's **Case** or attaches to the already-open one — at most one non-terminal Case per account, enforced by `aml_cases_one_open_per_account`.
- Ruling on an **Alert** is a **Human Disposition** through the existing MDR door; `aml-service` adds no disposition endpoint, so the case view and the MDR worklist stay two views of one fact.
- The first officer action on a **Case** — a Disposition on a linked Alert, or a **SAR draft** request — moves it `OPEN → INVESTIGATING`.
- A **Case** closes `CLOSED_DISMISSED` only while no linked Alert has an accept **Ruling in force**, and `CLOSED_REPORTED` only when the SAR decision's **Actionable output** is served through the **Gate** (ADR-0014); both closes are idempotency-protected officer actions.
- Case transitions are audit events (`AML_CASE_OPENED`, `AML_CASE_CLOSED_DISMISSED`, `AML_CASE_CLOSED_REPORTED`) on the `global` chain; Alerts and SAR drafts are Entries on the `mdr` chain — tamper evidence covers the investigation end to end.
- **Tipping-off** binds every other context: a monitored account's customer surfaces are indistinguishable from an unmonitored one's.

## Example dialogue

> **Dev:** "The sweep saw the same five payments again — do we raise another **Alert**?"
> **Domain expert:** "No. Same **Typology**, same **Evidence set** — same Alert. The dedup hash suppresses it; the worklist reflects patterns, not polling cadence."
> **Dev:** "The officer accepted the SAR draft, so the case closes itself?"
> **Domain expert:** "Nothing closes itself. Close-as-reported reads the ruling through the **Gate** at the moment of the officer's action — the ruling makes the narrative actionable; the action closes the **Case**."

## Mirrored outside this context

`malai-seeder`'s `AmlDemoSeed` builds the seeded demo Cases (AML issue 09) and cannot depend on this module — `onlyTheAggregatorDependsOn` allows only the `com.malai.api` aggregator to. It therefore restates two formulas so a seeded Alert is the one a live Sweep would have raised over the same payments: `AlertIdentity`'s decision- and entry-id derivation, and `MonitoredPayment.contentHash`. Change either and the seeded museum silently stops matching what the rules produce; update `AmlDemoSeed` and its golden values in the same commit. `AmlDemoSeedTest` pins them.

## Flagged ambiguities

- "alert" vs the screening Referral — resolved: a Referral parks one payment before money moves and stays customer-visible as a payment state; an **Alert** records a pattern after settlement, parks nothing, and is ops-only vocabulary.
- "case" — ADR-0012 anticipated "the case-management disposition flow arriving next" without naming an owner. Resolved: the **Case** lives here, not in `mdr-service` — the MDR stays ignorant of its consumers.
- "report" — v1 never files anything with any authority; `CLOSED_REPORTED` is the terminal demo state and the accepted **SAR draft** narrative is the artifact. Real NCA filing is out of scope by PRD.
- "risk score" — carried in the Alert's Verdict for reviewer ranking; not a customer risk rating (CDD/EDD out of scope) and not aggregated across Alerts. Unresolved: whether a Case-level severity derives from its Alerts once the Ops surface needs sorting beyond recency.
