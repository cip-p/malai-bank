# 06 — SAR draft: a grounded Model Decision

Status: ready-for-agent
Category: enhancement

## Parent

`.scratch/aml/PRD.md`

## What to build

The AI showpiece's first half. The request-SAR-draft action assembles the case's timeline — evidence payments, alert decisions and their dispositions, case audit events, each ref content-hashed — and invokes the `SarDraftDecider` seam: deterministic template drafter as the default in every profile (model id honestly named, version 1, prompt template `sar-narrative` v1), budget-bounded, following the Copilot decider's shape. The Anthropic adapter is a later, gated slice.

The draft is recorded as a non-advisory Model Decision, correlation ref CASE, its retrieved-context refs doubling as the citation whitelist. Grounding is machine-checked and all-or-nothing: a narrative citing outside the timeline (or citing nothing) fails the whole draft, which is recorded — raw output kept, verdict absent — then refused rather than served. Record-before-serve holds: if the MDR write fails, the request fails.

Requesting a draft is an officer action, so it moves an `OPEN` case to `INVESTIGATING` (the transition machinery from the dismiss-path slice).

## Acceptance criteria

- [ ] Acceptance: draft request records a non-advisory Model Decision with CASE correlation, cited refs, and the template drafter's honest model naming; response matches the contract strictly
- [ ] Acceptance: a draft violating grounding is recorded as a failure (raw output kept, verdict absent) and the request is refused
- [ ] Acceptance: MDR-write failure fails the draft request
- [ ] Acceptance: draft request on an `OPEN` case transitions it to `INVESTIGATING`
- [ ] Isolated tests: timeline assembly composes the decided record set with content hashes; grounding validator admits in-timeline citations and rejects strays, all-or-nothing
- [ ] Case detail shows the SAR state
- [ ] `mvn install` green

## Blocked by

- `05-case-detail-dispositions-dismiss-path.md`
