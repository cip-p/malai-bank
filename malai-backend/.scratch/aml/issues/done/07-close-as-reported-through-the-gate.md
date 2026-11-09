# 07 — Close-as-reported through the Gate

Status: ready-for-agent
Category: enhancement

## Parent

`.scratch/aml/PRD.md`

## What to build

The arc's enforcement beat. Close-as-reported consults the Gate synchronously at the moment of the officer's action: it reads the SAR decision's actionable output — an accept or edit ruling in force yields the (possibly edited) narrative, which is attached as the case transitions to `CLOSED_REPORTED` with its audit event. No ruling, or a reject in force, is a domain-coded 409 refusal and the case stays `INVESTIGATING` — conservative-only, exactly the ADR the bootstrap slice recorded. No resolver sweep: a human is already driving.

An edit ruling's diff stays derivable on the MDR record (read-time diff, never stored); the case carries the ruled narrative. The close action is idempotency-protected — a retried close enacts exactly once.

## Acceptance criteria

- [ ] Acceptance: accept ruling in force → narrative attached, `CLOSED_REPORTED`, `AML_CASE_CLOSED_REPORTED` audit event asserted as full entity
- [ ] Acceptance: edit ruling in force → the edited narrative is what attaches
- [ ] Acceptance: reject ruling in force → domain-coded 409, case stays `INVESTIGATING`
- [ ] Acceptance: no ruling yet → domain-coded 409, case stays `INVESTIGATING`
- [ ] Acceptance: retried close enacts exactly once
- [ ] Case detail after close shows terminal status and the attached narrative; strict payload assertions
- [ ] `mvn install` green

## Blocked by

- `06-sar-draft-grounded-decision.md`
