# PRD — Payment Screening: the MDR's First Live Decision Point

Status: ready-for-agent

Grilled 2026-07-19. Decisions recorded in ADR-0008 (un-ruled verdicts move the bank conservative-only) and ADR-0009 (referred payments move only via the MDR gate); language in `malai-services/mdr-service/CONTEXT.md` (Decision point, Shadow mode) and `malai-services/payments-service/CONTEXT.md` (Screening, Referral, Declined, extended Lifecycle).

## Problem Statement

The Model Decision Record is live, admin-surfaced, and demoable — but every Entry in it is seeded. The Ops surface is a museum: nothing a bank evaluator does in the customer app ever produces a Model Decision, no ruling ever moves money, and the actionable-output gate has no consumer. The platform's core claim — AI-assisted decisions, provably recorded, gated behind human rulings — cannot be shown happening; it can only be shown having happened, in fiction.

## Solution

Outbound payment screening becomes the MDR's first live Decision point. Every customer-initiated FPS and SWIFT payment is screened pre-dispatch by an honestly-named deterministic reference decider; the Model Decision — real rendered prompt, real template hash, real context refs, real latency — is chained into the MDR before the flow branches. In `SHADOW` mode the record is the whole effect. In `ENFORCE` mode (the demo posture) a flagging verdict parks the payment in the new `REFERRED` state, funds still held, and only a Human Disposition — consumed through the gate — moves it: accept the DECLINE and the payment is `DECLINED`; edit to an APPROVE and it releases, dispatches, and settles. The evaluator makes a payment in Banking, flips to the Ops surface, and watches the loop close for real.

## User Stories

1. As a bank evaluator exploring the customer app, I want my outbound payment to produce a Model Decision in the Ops worklist, so that the back office I was shown is visibly connected to the product I'm using.
2. As a bank evaluator, I want ordinary payments to complete exactly as before, so that screening never degrades the customer experience I'm evaluating.
3. As a demo presenter, I want a payment at or above the threshold to be flagged deterministically, so that I can produce a worklist item on cue by sending a large payment.
4. As a demo presenter, I want a payment to the watchlist counterparty flagged at any amount, so that I can trigger the beat without typing conspicuous zeros.
5. As a demo presenter, I want one armed amount that makes the decider time out, so that I can show live that a missing output is itself a recorded fact.
6. As an ops reviewer, I want a live screening decision to carry the rendered prompt, retrieved-context refs with content hashes, raw output, and verdict, so that replay reconstructs exactly what the decider saw and produced.
7. As an ops reviewer, I want the record to name the model that actually ran, so that the audit plane never claims an invocation that didn't happen.
8. As an ops reviewer, I want the WATCHLIST context ref's content hash to pin the watchlist version that flagged a payment, so that I can prove eighteen months later which list fired and that it hasn't been doctored.
9. As a bank evaluator in `ENFORCE` mode, I want a flagged payment to land in `REFERRED` with my funds still held, so that money under review can't be double-spent.
10. As a bank evaluator, I want my referred payment visible with its state and nothing else — no reason code, so that the bank never tips off a suspected fraudster (and I see that the platform knows this rule).
11. As an ops reviewer, I want accepting a DECLINE verdict to decline the referred payment, so that ruling on the output is what moves the money.
12. As an ops reviewer, I want editing the verdict to an APPROVE to release the payment to its rail, so that a human override is enacted and the diff stays on the record.
13. As an ops reviewer, I want a reject ruling to leave the payment parked, so that throwing away bad output never silently decides a payment's fate.
14. As an ops reviewer, I want a later disposition on a rejected decision to re-govern the payment, so that latest-wins rulings keep every parked payment resolvable.
15. As a bank executive watching a demo, I want the released payment to then settle through the normal rail path, so that the enforcement loop composes with the payments machinery I've already seen.
16. As a compliance officer, I want the Model Decision appended before the payment proceeds or parks, so that no screened action ever precedes its record.
17. As a compliance officer, I want a decider timeout or error recorded as its own Outcome while the payment proceeds, so that screening availability never silently blocks customers and the gap is still on the record.
18. As a compliance officer, I want the payment to fail when its screening record cannot be written, so that "every decision is recorded" is enforced, not aspired to.
19. As a demo presenter, I want chain verification to walk live entries and seeded entries alike, so that tamper-evidence covers the decisions the audience just watched being made.
20. As a real-bank deployer, I want screening to default to `SHADOW` mode, so that adopting the platform never opts me into enforcement I didn't configure.
21. As a real-bank deployer, I want the shadow-to-enforce rollout to be one property, so that the adoption path the sales demo describes is literally true.
22. As a bank evaluator holding the admin hat, I want the decisions my payments generate to be rulable by me on the Ops surface, so that one login demonstrates both sides of the loop.
23. As an ops reviewer, I want a retried ruling to enact exactly once, so that a flaky network can neither fork the record nor double-move a payment.
24. As a platform maintainer, I want the decider behind a seam owned by the consuming flow, so that wiring a real model later changes an implementation, not an architecture.

## Implementation Decisions

- **Coverage**: customer-initiated outbound FPS and SWIFT payments. Book transfers (atomic, no scheme exposure), inbound payments, and scheduled/internal executions are excluded; inbound and scheduled are named future Decision points, and ambient standing-order screening is noted as a future worklist-liveness idea.
- **Placement**: screening runs synchronously inside payment initiation, after the ledger Hold and payment persistence, before rail dispatch. The Model Decision is always recorded before the flow branches (record-before-action). The referred path simply never dispatches.
- **Conservative-only invariant (ADR-0008)**: an un-ruled Verdict may only add friction. Proceed-on-no-flag is the standing default acting, not the verdict acting. Moving a `REFERRED` payment requires Actionable output under a Ruling in force.
- **The decider**: a `ScreeningDecider` seam owned by the payments flow, with a deterministic rules implementation as sole production impl — threshold (≥ £10,000 flags), one watchlist counterparty from the stub's fictional directory (flags at any amount), one armed exact amount simulating a timeout, riskScore derived deterministically from amount band + watchlist hit. Recorded honestly as `malai-screening-rules` version `1`. No LLM or provider dependency; a future model adapter is a new seam implementation and must respect the in-request latency budget or move the Decision point async — its problem, not this increment's.
- **Prompt and context**: a real, versioned `payment-screening/v1` Prompt template with a genuine content hash. The Rendered prompt assembles the payment's facts (amount, currency, rail, beneficiary name and masked identifiers, payment purpose when international) plus a payer-account snapshot without balances. Retrieved-context refs: `ACCOUNT` (hash over the account lookup actually used) and `WATCHLIST` (id = watchlist version, hash over its content). Correlation ref: `PAYMENT` / the payment id. Nothing appears in the prompt the decider didn't have.
- **Recording**: via a new internal MDR client pair following the established client-module pattern; entry ids minted inline per screening via the platform UUID generator (the write is synchronous and one-shot; the client-supplied entry id remains the natural idempotency key).
- **Failure semantics**: decider timeout/error → `TIMED_OUT`/`ERRORED` Entry appended, payment proceeds (fail-open; a fail-closed-on-absence policy is a legitimate per-template choice a real bank could make — noted, not built). MDR write failure → the payment request fails (fail-closed), identical in both modes. Cleanup of the placed Hold on that path follows the existing dispatch-failure behaviour — verify at implementation, not a new product decision.
- **Mode**: `malai.payments.screening.mode`, `SHADOW` (default) or `ENFORCE` (set by the demo environment), branching only the policy step — recording always happens. Mirrors the demo-all-roles property precedent.
- **Lifecycle**: new `REFERRED` state (UK ops argot) and terminal `DECLINED` state. `REFERRED → PENDING` on release (then the normal rail path), `REFERRED → DECLINED` on an adverse ruling (Hold released, nothing posted, no scheme traffic — the payment never existed to the outside world). Invariants: a `REFERRED` payment has exactly one active Hold and no ledger entries; a `DECLINED` payment has neither.
- **Enforcement consumption (ADR-0009)**: a new internal actionable-output endpoint on the MDR (service-JWT scoped, a thin second door onto the existing gate logic — the ruling-in-force translation stays in one place). A resolver in payments sweeps `REFERRED` payments on a ~5-second property-tunable cadence, consults the gate via the client, and enacts the actionable verdict: `APPROVE` releases, `DECLINE` declines, 409 leaves it parked, an unparseable actionable output conservatively leaves it parked. The decision id is stored on the payment row as an opaque pointer, never a foreign key. Transitions fire only from `REFERRED`, so re-polls and retries are naturally idempotent. An SQS disposition-appended event is the named production upgrade path; the gate remains the confirmation surface even then.
- **Contract**: the public payment-state enum gains `REFERRED` and `DECLINED` — a deliberate, coordinated change (the compatibility check will flag response-enum widening; approve it consciously). No customer-facing decline reason (anti-tipping-off). Internal MDR routes stay out of the public contract per the documented convention.
- **Schema**: one migration — the payments table admits the two new states and gains a nullable decision-id column.
- **Audit**: three new audit event types for referral, release, and decline, joining the existing payment lifecycle family.
- **Configuration blast radius**: the new client's base-url property must be declared in every compile-scope consumer's main, test, and acceptance-boot configuration, plus the shared client-test defaults — the known new-client gotcha, handled as part of the client-pair work.

## Testing Decisions

- A good test asserts external behaviour — response payloads, database rows as full entities, recorded MDR entries, emitted audit events, HTTP/SQS interactions on the diagram — never decider internals or orchestration structure.
- **Payments acceptance (Kensa, happy/sad)**: shadow mode records-and-proceeds for FPS and SWIFT; threshold, watchlist, and armed-timeout verdicts; enforce mode parks flagged payments as `REFERRED` with Hold retained and no dispatch; the resolver enacts accept-DECLINE → `DECLINED` (Hold released, nothing posted), edit-APPROVE → `PENDING` + dispatch → settles; reject leaves `REFERRED`; MDR-write failure fails the payment; decider failure records and proceeds; new audit events; full-entity DB assertions throughout (these will also absorb the test-UUID queue shift the new mint introduces). Prior art: the existing outbound payments acceptance tests and their WireMock/SQS lane capture; the MDR service gains a WireMock lane in these diagrams.
- **MDR acceptance**: the internal actionable-output endpoint — happy (accept serves raw output, edit serves edited output) and sad (no ruling and reject 409, unknown decision 404, missing service JWT 401). Prior art: the existing gate and internal-write test classes.
- **Client pair**: WireMock-backed request-construction and deserialisation tests per the client-module convention. Prior art: the account client's tests.
- **Decider direct tests**: the deterministic rules — threshold boundary, watchlist hit, armed timeout, riskScore bands, verdict shape — exercised as a pure module. Deviation from acceptance-only is deliberate: the rules are the volatile part and deserve fast feedback.
- **Repository tests**: any new custom finder (e.g. by `REFERRED` state), per the house rule.
- **e2e**: one screening journey — flagged payment lands `REFERRED`, decision appears in the worklist, disposition appended, payment moves, chain verification walks the live entries. Prior art: the existing MDR e2e flow and payments e2e tests.

## Out of Scope

- The exec metrics API and the fraud-prevented number (falls out of accept-DECLINE rulings in a later increment).
- Webapp work: rendering `REFERRED`/`DECLINED` in Banking, and any Ops-surface change (none is needed — the worklist fills as-is). Tracked on the webapp side.
- Card-authorisation and transaction-categorisation Decision points; inbound and scheduled-payment screening.
- A real LLM/provider adapter behind the seam, and any per-template fail-closed policy machinery.
- SQS disposition-appended events (named upgrade path only).
- Changes to seeded MDR data — the seeds keep their fiction; a mixed-model worklist is realistic.
- Customer-initiated cancellation of a `REFERRED` payment.

## Further Notes

**Hand-offs:** malai-infra sets the screening mode to `ENFORCE` in the demo environment's task definition; the webapp increment consumes the widened payment-state contract (its brief should carry the no-decline-reason copy rule); the demo script gains the flagged-payment beat once enforcement lands.

**Slicing note for the issue pass:** shadow mode is the tracer bullet — client pair, seam, decider, recording, mode property, shadow acceptance + e2e — shippable before any lifecycle, contract, or resolver work begins. Enforcement (states, migration, internal gate endpoint, resolver, contract widening) stacks on top without reopening the shadow slice.
