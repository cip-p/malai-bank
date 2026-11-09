# 01 — Explain this payment, on the Decision page

Status: ready-for-agent
Category: enhancement

## What to build

The Ops surface gains the Copilot. `DecisionPage` — where a reviewer is already deciding whether to accept a DECLINE — gains an "Explain this payment" action for decisions whose `correlationType` is `PAYMENT`, calling `POST /api/v1/admin/copilot/explanations` with `{ subjectType: "PAYMENT", subjectId: correlationId }` and an `Idempotency-Key`.

Backend contract (see `malai-backend/.scratch/copilot/PRD.md`): the response is `{ decisionId, outcome, claims: [ { text, sources: [ { type, id } ] } ] }`. Regenerate the client from the published `openapi.yaml` rather than hand-writing types.

Three states to render:

- **Grounded** — the ordered claims, each with its source ids visible beside the text (not hidden behind a hover), plus a link to the Copilot's own MDR entry via `decisionId`, so the explanation's own record is one click away.
- **Refused** — `outcome` is `COMPLETED` with no claims: say plainly that an answer was produced and failed grounding validation, and link to the MDR entry where the raw output can be read. This state is a feature, not an error; it should not look like a crash.
- **No answer** — `TIMED_OUT` or `ERRORED`: no answer was produced, the record exists, link to it.

The explanation is **non-authoritative** (ADR-0012) and must read that way on screen: it aids an investigator, it is not a finding, and nothing in the product acts on it.

The worklist's default view narrows to non-advisory decisions awaiting a ruling — `useMdrDecisions` passes the new `advisory` filter — so copilot entries never appear as work (ADR-0013).

## Acceptance criteria

- [ ] The explain action appears only on decisions with `correlationType === "PAYMENT"`
- [ ] Claims render in order with their source ids visible, and the MDR entry for the explanation is linked
- [ ] The refused state renders distinctly from the error state and from an empty state, and links to the raw output
- [ ] Timed-out and errored outcomes render as "no answer was produced", with the record linked
- [ ] The request carries an `Idempotency-Key`, and a double-click does not fire two invocations
- [ ] The worklist's default view excludes advisory decisions
- [ ] The types are generated from the published contract, not hand-written
- [ ] Vitest covers all three states and the correlation-type gating; `npm test` and the a11y spec green

## Blocked by

- `malai-backend` issues 03 and 04 (the `advisory` filter and the explanations route)
