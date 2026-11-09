# Model Decision Record (MDR)

The append-only, hash-chained audit plane for AI-assisted decisions: what a model was asked, what it produced (or failed to produce), and what a human ruled about it. It exists so that any decision can be reconstructed and proven intact long after the fact, independent of any model provider.

## Language

**Model Decision Record (MDR)**:
The bounded context itself — the tamper-evident record of AI-assisted decisions and their human rulings.
_Avoid_: model log, AI audit log

**Entry**:
One immutable, chained row in the MDR — either a Model Decision or a Human Disposition.
_Avoid_: record (overloaded with the R in MDR), event (that's the audit plane's word)

**Model Decision**:
An Entry recording a single model invocation — who invoked it, against which model and prompt template, what came back, at what latency and cost.
_Avoid_: prediction, inference, completion

**Human Disposition**:
An Entry recording a human's ruling on a prior Model Decision — accept, edit (carrying the full edited output), or reject — appended as a new Entry, never mutating the one it rules on. The diff shown to reviewers is derived at read time from the two immutable outputs, never stored.
_Avoid_: review, approval, override (taken by `insights-service`), update

**Decision**:
The logical unit identified by `decision_id`: a Model Decision together with the Entries that reference it.

**Outcome**:
How a Model Decision's invocation concluded — with output (*completed*) or provably without (*timed out*, *errored*); exactly one Entry is appended either way.
_Avoid_: status (implies future transitions; an Entry never changes)

**Rendered prompt**:
The exact (redacted) text presented to the model after template resolution and context injection — stored inline in the Entry so replay never depends on another store surviving.
_Avoid_: prompt (ambiguous between template and rendered form)

**Prompt template**:
The git-versioned skeleton a Rendered prompt was produced from, pinned in the Entry by id, version, and content hash.

**Raw output**:
The model's output verbatim modulo platform redaction — sensitive shapes become `[REDACTED]` before the output is hashed and stored, exactly as the audit plane treats payloads.
_Avoid_: response (HTTP word), completion

**Verdict**:
The structured reading parsed from a Model Decision's Raw output; absent when unparseable or not produced. Its shape is defined by the Prompt template and deliberately unconstrained in the record — consumers treat it as opaque structured JSON.
_Avoid_: result, outcome (taken by Outcome)

**Correlation ref**:
An opaque (type, id) pointer from an Entry to the business resource the decision concerns (a payment, an account, a case). Never a foreign key into another context's tables.
_Avoid_: resource link, FK

**Chain**:
The MDR's own hash-chain scope (`mdr`) — second tenant of the platform chain machinery beside the audit plane's `global` scope (ADR-0007).

**Ruling in force**:
The most recent Human Disposition for a Decision; earlier rulings stay in the record but no longer govern.
_Avoid_: current status, final decision

**Actionable output**:
What a consumer may act on: the Raw output when the Ruling in force is *accept*, the stored edited output when it is *edit*, nothing under *reject* or when no ruling exists.

**Gate**:
The read that serves Actionable output and refuses when none exists — the MDR's single enforcement surface (ADR-0009). It has two doors, an admin one for the Ops surface and a service-JWT one for consuming flows, onto one implementation: the accept/edit/reject → Actionable output translation lives in exactly one place, and no consumer re-derives what *actionable* means. A refusal is a domain-coded 409, which a consumer reads as "leave it as it is", never as a failure.
_Avoid_: approval check, permission check, authorisation (that's the cards word)

**Decision point**:
A place in a business flow where a model is invoked and its Model Decision recorded — the MDR's consumer side.
_Avoid_: integration, hook, call site

**Advisory**:
A property a **Model Decision** declares about itself: no flow waits on it, so no consumer will ever request its **Actionable output** and no reviewer owes it a ruling. It forbids nothing — a **Human Disposition** may still be appended, and the **Gate** answers as it always does — it only keeps a reviewer's worklist to the decisions that hold something up.
_Avoid_: informational, non-blocking, read-only (that describes the consumer, not the decision); **Non-authoritative** (the `copilot-service` word for what may be done with an output).

**Shadow mode**:
A Decision point that records Model Decisions without letting any Verdict influence the flow — the rollout stage before enforcement.
_Avoid_: dry run, dark launch

## Relationships

- A **Decision** groups one **Model Decision** and its **Human Dispositions**.
- A **Human Disposition** references exactly one **Model Decision**; a Model Decision may accrue several Dispositions over time.
- Every **Entry** is linked to its predecessor in the **Chain**, so a Disposition's hash transitively covers the Model Decision it rules on.
- The **Ruling in force** is the latest **Human Disposition**; **Actionable output** exists only while that ruling is *accept* or *edit*.
- An un-ruled **Verdict** may only move a **Decision point**'s flow in the conservative direction — add friction, never authorise movement; acting on a Decision requires **Actionable output** under a **Ruling in force** (ADR-0008).
- An **Advisory** Model Decision is recorded, chained and replayable like any other, and both the worklist summary and the replay view declare the flag, so an Entry says for itself whether a ruling was ever expected of it; it is excluded only from the reviewer's default worklist, never from the record.
- A **Decision point** learns its **Ruling in force** only through the **Gate**, never by reading **Human Dispositions** — so the MDR stays ignorant of its consumers and its consumers stay ignorant of its internals (ADR-0009).

## Example dialogue

> **Dev:** "The reviewer wants to fix a typo in a model's output — do we update the **Entry**?"
> **Domain expert:** "Never. They append a **Human Disposition** of kind *edit*, carrying the diff. The original **Model Decision** stays exactly as the model produced it — that's the whole point."
> **Dev:** "And if the model timed out and there's no output?"
> **Domain expert:** "The absence is the fact. A **Model Decision** Entry is still appended; a missing output is recorded, not skipped."

## Flagged ambiguities

- "hash-chained ledger" — the client brief conflated the ledger with the chain. Resolved: the **Ledger** has no hash chain (its integrity is double-entry sum-to-zero); the only chain machinery is the audit plane's, and the MDR is its second tenant.
- "actor" — the audit plane's `ActorType` vocabulary is `CUSTOMER | SUPPORT | ADMIN | SYSTEM`; the MDR needs *model vs human*. Unresolved: whether MDR actor kinds extend the audit vocabulary or stand alone.
- "from the record alone" (replay) vs "retrieved-context refs" — the brief demanded both; they can't both be literal. Resolved: the **Rendered prompt** is stored inline (the *what* the model saw), while context refs + content hashes provide provenance (the *where-from*); refs point at mutable rows in other contexts and are detection, not recovery.
- "synthetic marker" — the brief assumed a platform-wide marker exists; it does not (no column, annotation, or flag anywhere). Resolved: the MDR satisfies the synthetic-data boundary the way every other table does — architecturally (no real rails, fictional counterparties) — with no per-row marker.
- "verdict envelope" — whether the contract should type the **Verdict**'s shape. Resolved: no stable envelope exists — key-sets differ per **Prompt template** (`fraud-triage` → `decision`/`riskScore`; `transaction-categorisation` → `category`/`confidence`; `spending-insights` → `insight`; timed-out and errored decisions carry none), and typing one would force the contract to track every template, against the MDR stance that the record stores what the model actually produced. The shape is template-defined via `promptTemplateId` and deliberately unconstrained on both the write request and the replay view; consumers treat it as opaque structured JSON. Stated on the `verdict` field in `openapi.yaml`.
