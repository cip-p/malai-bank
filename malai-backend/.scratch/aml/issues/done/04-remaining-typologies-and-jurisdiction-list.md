# 04 — Remaining typologies and the jurisdiction list

Status: ready-for-agent
Category: enhancement

## Parent

`.scratch/aml/PRD.md`

## What to build

The decider learns the other two patterns. Rapid in-out: an inbound credit followed by outbound transfers moving at least a configured percentage of it within a short window. High-risk corridor: a SWIFT payment whose beneficiary jurisdiction — derived from IBAN characters 1–2 or BIC characters 5–6, no schema change — appears on a versioned fictional high-risk list, configured with a version string exactly like the screening watchlist, its content hash pinned in every corridor alert's context ref.

Both typologies flow through the tracer's existing machinery unchanged: per-(account, typology, evidence set) alerts, evidence-hash dedup, auto-open-or-attach case behaviour.

## Acceptance criteria

- [ ] Isolated rule tests: percentage-exact drains, in-out window boundaries, jurisdiction derivation from IBAN and from BIC, list membership versus absence
- [ ] Acceptance: each typology raises its alert with typology-correct verdict and evidence refs
- [ ] Acceptance: corridor alert's context ref pins the jurisdiction-list version by content hash
- [ ] Acceptance: an alert for an account with an open case attaches; different typologies on one account share the case
- [ ] `mvn install` green

## Blocked by

- `03-tracer-structuring-caught-end-to-end.md`
