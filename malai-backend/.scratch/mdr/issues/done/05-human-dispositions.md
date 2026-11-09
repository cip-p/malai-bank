# 05 — Human Dispositions: accept / edit / reject append to the chain

Status: ready-for-agent

## Parent

`.scratch/mdr/PRD.md` (Model Decision Record)

## What to build

An ADMIN-role human appends a Human Disposition Entry ruling on a prior Model Decision. ACCEPT and REJECT carry the ruling alone; EDIT carries the full edited output (redacted), which becomes the hash-covered actionable artifact — no diff is stored, ever. The Disposition chains after the decision it rules on, so its hash transitively covers exactly what the human saw. Nothing is ever updated in place; a decision may accrue several Dispositions over time.

## Acceptance criteria

- [ ] `POST /api/v1/admin/mdr/decisions/{decisionId}/dispositions` (ADMIN role) appends ACCEPT and REJECT Dispositions with the parent-entry link to the Model Decision and the ruling human's id
- [ ] EDIT requires and stores the complete edited output, redacted before hashing and persisting; the shape CHECK enforces presence for EDIT and absence otherwise
- [ ] The Disposition is chained after the decision: hash linkage asserted in an acceptance test
- [ ] Multiple Dispositions on one decision append cleanly (the correction path for latest-wins, exercised in slice 06)
- [ ] Sad paths: unknown decision id, Disposition targeting a Disposition, non-ADMIN caller
- [ ] Each mutation records an ordinary audit event; Kensa happy/sad tests with strict-match contracts and full-entity database assertions

## Blocked by

- `02-record-model-decision-tracer.md`
