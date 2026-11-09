# 04 — The SAR panel

Status: ready-for-agent

## Parent

`.scratch/aml/PRD.md`

## What to build

The Case view's SAR panel, owning the SAR arc end-to-end. A pure state-derivation module maps (the case detail's SAR state, the fresh draft response, the case status) to exactly one panel state:

- **No draft, non-terminal Case** — the request affordance. Requesting invokes the draft endpoint with a stable per-invocation idempotency key (the copilot invocation precedent), so a retry replays rather than forks the record.
- **Fresh grounded draft** — the narrative rendered inline from the invocation response, ephemeral by design (the case read never carries a draft narrative), its decision id linked.
- **Fresh refused draft** — the grounding-failure state rendered as a recorded fact: refused, the raw output still readable at the linked record, never an error toast.
- **Fresh timed-out / errored invocation** — the missing answer as a recorded fact with its decision link.
- **Revisit with a draft on record** — drafted status plus the ruling-in-force tag, linking to the Decision view to read and rule.
- **Closed reported** — the attached narrative verbatim from the case read, permanent.

The decision id is linked in every post-request state. Ruling on the draft stays on the Decision view — the panel never grows an accept/reject affordance.

Demoable alone: request a draft on the seeded open Case and watch the narrative land inline; navigate away and back to see the honest revisit state.

## Acceptance criteria

- [ ] Each panel state above renders as described, with the decision id linked in every post-request state
- [ ] The draft mutation's idempotency key is stable across a retry of the same invocation and fresh for a new one
- [ ] The grounding-refused, timed-out, and errored states render as designed facts, never toasts
- [ ] The attached narrative renders verbatim and permanently once the Case is closed reported
- [ ] The state derivation is a pure module tested in isolation across every state, including ephemeral-versus-revisit
- [ ] Colocated unit tests per house style; lint, prettier, and the unit suite green

## Blocked by

- `02-case-view-assembly.md`
