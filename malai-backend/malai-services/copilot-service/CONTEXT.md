# Copilot

The bank's read-only investigation aid: given a reference to something that happened, it reconstructs the story in language, with every factual assertion tied to the record it came from. It moves no money, initiates nothing, and rules on nothing — it hands an investigator a cited account of events they could have assembled by hand. Every answer it gives, and every answer it fails to give, is a Model Decision on the Model Decision Record.

## Language

**Copilot**:
The bounded context itself — the surface that turns records into a cited explanation of why something happened.
_Avoid_: "Ledger Copilot" in code and glossary (the demo label survives, but this context is not part of the **Ledger** and reads far more than ledger entries); assistant, agent, chatbot.

**Explanation**:
One answer about one **Subject**: an ordered set of **Claim**s produced by a single model invocation. An Explanation exists only when every Claim in it is grounded; a produced-but-ungrounded answer leaves a record and no Explanation.
_Avoid_: answer, summary, narrative, report.

**Subject**:
The `(type, id)` reference an Explanation is about — the same shape as the MDR's **Correlation ref**. v1 resolves `PAYMENT` only.
_Avoid_: case (case management does not exist yet), query, target.

**Claim**:
One factual assertion inside an Explanation, carrying the **Source record**s it rests on. A Claim citing nothing, or citing outside the **Timeline**, is not a Claim — it is a grounding failure.
_Avoid_: statement, finding (a finding is a human's conclusion; the Copilot never produces one), fact.

**Source record**:
One immutable record admitted to a **Timeline** and citable by a Claim — a payment, an audit event, a ledger entry, a hold, a standing order, a Model Decision — identified by `(type, id)` and pinned by a content hash.
_Avoid_: document (nothing here is retrieved from prose), chunk, evidence.

**Hold**:
The ledger reservation placed for an outbound payment, admitted to the **Timeline** as a Source record so the money's whereabouts is citable rather than merely summed into a balance. Its id *is* the payment's id — payments-service places, settles and releases the hold under `holdId = paymentId` — which is the only link between the two, there being no column joining them. A `PLACED` hold on a referred payment is the fact that the funds are reserved and unspent; `RELEASED` is the reservation going back on a decline; `SETTLED` is it becoming ledger entries.
_Avoid_: block, freeze, pending transaction (a hold posts nothing, so it is not a transaction).

**Screening decision**:
The decision a payment was referred under, admitted to the **Timeline** as a Source record named by the payment row's `screeningDecisionId`. Its substance — that a decision was recorded, and the **Human Disposition** ruled on it — is read from the audit plane against `resourceType = "ModelDecision"`, never from the MDR, which stays ignorant of this consumer (ADR-0009). Absent from the Timeline entirely when the payment was never referred.
_Avoid_: screening verdict (the Copilot cites the decision's record, not a verdict it never sees), MDR entry (nothing here reads the chain).

**Timeline**:
The ordered set of Source records assembled for a Subject, and the only material the **Decider** is ever shown. Nothing outside the Timeline can be cited, so the Timeline defines what an Explanation is permitted to say.
_Avoid_: context (overloaded), history, feed (a feed is the customer-facing account list).

**Grounding**:
The check that every Claim cites Source records drawn from the Timeline. It is machine-verified on every answer, not requested of the model — the Copilot does not trust the Decider to cite honestly.
_Avoid_: citation check, validation, RAG (nothing is retrieved from documents).

**Decider**:
The seam through which the Copilot invokes a model, named after the same seam in payment **Screening**. It is handed a **Timeline** and the **Rendered prompt** and answers with **Claim**s, the model that actually answered, and what the invocation cost — the three facts only the implementation can know, so a record stays honest whichever one is selected. Which implementation the Copilot gets is a property, `malai.copilot.decider`; the deterministic one is the default in every profile, including test and e2e.
_Avoid_: model, provider, LLM (those name what may sit behind the seam, not the seam).

**Provider**:
The outside service a **Decider** implementation calls a real model on, selected by property and never the default. The adapter sends the **Rendered prompt** as the whole of its message and adds no prose of its own; the answer's shape is constrained by a JSON schema the adapter declares, which carries structure and no subject data, so the stored Rendered prompt remains the complete account of what left the estate. What it is billed — the resolved model, the tokens, the currency — is read back off the invocation and recorded, never assumed.
_Avoid_: vendor, backend, integration; **Decider** (that is the seam, not what it calls).

**Refusal**:
What happens to a produced answer that fails **Grounding**: it is recorded whole and withheld whole. The surface is told which of the two silences it is looking at — an answer refused, or no answer at all — and never told a refused answer's contents.
_Avoid_: rejection (a reviewer rejects; the platform refuses), error (a refusal is a concluded invocation, not a fault), filtered.

**Budget**:
The bounded time the **Decider** is given. Exceeding it abandons the invocation and records it as timed out, so a slow model costs a recorded fact rather than a hung request.
_Avoid_: SLA, deadline (the invocation is abandoned, not merely late), retry window (there is no retry).

**Non-authoritative**:
The standing property of every Explanation — it aids an investigator and is never itself a finding, a disposition, or a reason a thing happened. Nothing in the platform may consume an Explanation as input to an action.
_Avoid_: advisory (that word is taken by the MDR, where it describes a **Model Decision** no flow waits on).

## Relationships

- A **Subject** resolves to exactly one **Timeline** per request; the Timeline is reassembled per invocation and never stored on its own.
- An **Explanation** is composed of ordered **Claim**s; each Claim cites one or more **Source record**s, and every cited Source record is in the Timeline.
- Every invocation — grounded, ungrounded, timed out or errored — produces exactly one **Model Decision** in the MDR, whose **Retrieved context ref**s are the Timeline's Source records and whose **Verdict** is the grounded Claims, absent when **Grounding** fails.
- A Copilot Model Decision is **Advisory** in MDR terms: no flow waits on it, and no consumer may request its **Actionable output**.
- The **Copilot** reads every context through its client, and the audit plane through the shared reader; it owns no tables and writes nothing but its own record.
- A referred payment's **Timeline** carries its **Screening decision** and the audit events about it, so the referral, the ruling and the release or decline are each citable — assembled from the payment row and the audit plane alone, with no MDR read.
- The **Timeline** orders by moment, then by record kind: payment, **Hold**, **Screening decision**, audit event, ledger entry. The sort is stable, so ledger legs keep the posting engine's debit-first order.
- Audit events are read against two resources — the payment and its **Screening decision** — and merged by chain sequence before that sort, because the chain's `seq` is a total order across resources while `ts` is not. Two events sharing a moment therefore narrate in the order they were appended, so a ruling always precedes the release or decline it caused.
- A **Provider**'s own failures land in the vocabulary the Copilot already has: a declined answer or one cut off at its output ceiling is an error carrying the provider's reason, a read timeout keeps the **Budget**'s timeout vocabulary rather than adding a second one, and an answer citing a record outside the **Timeline** is a **Refusal** exactly as the deterministic decider's armed subject is. No provider failure needs a word the platform did not already have.
- Every **Source record** is redacted before it reaches the rendered prompt: account numbers and IBANs are masked to their last four, while sort codes, balances and held amounts are shown in full, because a sort code names a bank and the available balance at the moment of the attempt is frequently the whole answer. The payment record achieves this structurally, from typed fields; an audit event cannot, because its `afterState` is an untyped map written by whichever service recorded it, so the masking there is by key name.

## Example dialogue

> **Dev:** "The model wrote a perfectly sensible sentence but cited a ledger entry that isn't in the **Timeline**. Do we show the rest?"
> **Domain expert:** "No. Grounding is all-or-nothing — one uncitable **Claim** and there is no **Explanation**, only a record that an answer was produced and refused. A half-shown narrative is worse than none, because the reader can't see what we took out."
> **Dev:** "And if the reviewer disagrees with an Explanation — do they reject it, like a screening verdict?"
> **Domain expert:** "They can append a **Human Disposition**, the record allows it. But nothing waits on that ruling, because nothing may act on an Explanation at all. It's **Non-authoritative** by construction."

## Flagged ambiguities

- "Ledger Copilot" — the brief's name implies membership of the **Ledger** context, which owns the book of record and knows nothing of payments or rails. Resolved: the context is **Copilot**; "Ledger Copilot" is a demo label only.
- "case reference" — the brief admits an account, payment, or case reference as a **Subject**. Resolved: no Case exists (case management is a later feature) and `ACCOUNT` is deferred; v1 resolves `PAYMENT` alone, on a ref shape that admits the others without a contract change.
- "scheduler runs, CoP verdicts, forecast records" — the brief named these as grounding sources. Resolved: none of them exists as a record. A scheduler firing leaves a payment and audit events, not a run row; a **Confirmation of Payee** verdict is computed and discarded; a balance forecast is derived per request. The Copilot cites only what is genuinely recorded, and explains a short forecast from the standing orders and mandates whose occurrences fall in the window.
- "verdict" — the MDR's **Verdict** (the structured reading of a Raw output) and a **Screening** verdict (approve/decline) are both in play. Resolved: an Explanation's Claims *are* the MDR Verdict for a Copilot decision; the Copilot has no verdict of its own and never decides anything.
- "the ledger entries of the payment's trace" — the **Timeline**'s ledger legs are found by `payment.traceId`, which payments-service reads from `MDC.trace_id` at write time. Nothing in this repo puts it there: in production the OpenTelemetry agent's logback-mdc instrumentation does, attached by the `JAVA_TOOL_OPTIONS` the ECS task definition sets. Wherever the agent is absent the payment persists a null trace and the Copilot silently cites no ledger entry at all — the demo's "and here is the money moving" beat, gone, with no error to notice. Resolved *for the e2e stack only*, by a profile-gated filter standing in for the agent. **Unresolved for local runs**: `start-malai-api-local.sh` is a plain `java -jar`, so a locally demonstrated Explanation still omits its ledger entries. The honest fix is for the platform to mint its own trace id rather than inherit one from an observability agent, which is a cross-cutting write-path decision deserving its own ADR.
- "real cost in the currency actually billed" — a **Provider**'s price list lives outside this repo and moves without warning, so the adapter carries per-token prices as configuration with the current published rates as defaults rather than a hardcoded table that silently goes stale. Resolved: **the cost is honest about arithmetic, not about the price list** — the tokens and the resolved model come off the invocation and cannot drift, but a deployer who changes the model and forgets the prices records a wrong figure, and nothing detects it. The adapter logs the resolved model beside the tokens and the cost so the two can be reconciled against an invoice. A price table keyed by model would close this, at the cost of a table this repo would have to keep current.
- "the originating standing order or mandate" — a scheduled payment's instrument is not a **Source record**, and cannot become one by reading forward. The link is a one-way hash: schedulers stamp `endToEndId = ScheduledPaymentEndToEndId.forOccurrence(instrumentId, occurrenceDate)`, so there is no column to join on. Resolved: **not built by the forward path**. `getActiveStandingOrders` and `getActiveDirectDebits` resolve *active* instruments only, so a cancelled instrument that caused a past payment would stay invisible — and a Timeline that carries the instrument for live instruments and silently omits it for cancelled ones is worse than one that never offers it, because the reader cannot tell the two silences apart. The honest fix is a reverse link on the payment row, populated on both scheduler paths; that is a write-path migration, not a Timeline read.
