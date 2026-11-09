# 05 — Case detail, dispositions, and the dismiss path

Status: ready-for-agent
Category: enhancement

## Parent

`.scratch/aml/PRD.md`

## What to build

The investigation view and the noise exit. The case detail endpoint assembles the case's whole story: its alerts (decision ids exposed so the Ops surface can drive the existing MDR disposition door), the evidence payments behind each, the dispositions ruled so far, the case status, and the SAR state.

Alert dispositions themselves need no new endpoint — the MDR admin surface is the door. What this slice adds is the case side of the same fact: a Human Disposition on a linked alert clears it from both views, and the first officer action on a case (first disposition on a linked alert, or a SAR draft request) moves `OPEN → INVESTIGATING`.

Close-as-dismissed lands the workflow's reject path: idempotency-protected, emitting `AML_CASE_CLOSED_DISMISSED`, and refused with a domain-coded 409 while any linked alert has an accept ruling in force — a case with confirmed suspicion cannot be waved away.

## Acceptance criteria

- [ ] Case detail matches the contract with strict payload assertions, including alert decision ids, evidence payments, disposition state, and SAR state
- [ ] Acceptance: first disposition on a linked alert transitions the case `OPEN → INVESTIGATING`; full-entity database assertion
- [ ] Acceptance: a REJECT-ruled alert shows as cleared in case detail and no longer awaits ruling on the MDR worklist
- [ ] Acceptance: close-as-dismissed succeeds when no accept ruling stands; `AML_CASE_CLOSED_DISMISSED` audit event asserted as full entity
- [ ] Acceptance: close-as-dismissed refused (domain-coded 409) while an accepted alert stands; case state unchanged
- [ ] Acceptance: retried close enacts exactly once
- [ ] Repository tests for any custom case queries
- [ ] `mvn install` green

## Blocked by

- `03-tracer-structuring-caught-end-to-end.md`
