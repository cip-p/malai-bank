# 10 — Seeded MDR demo state: a mixed-state Decision worklist out of the box

Status: done

## What to build

A freshly seeded environment has an empty MDR — the worklist, replay view, and gate would all demo nothing. Seed a small fixed set of Model Decisions covering every Outcome and every ruling state, so the ops pages have a live-looking worklist on first open: a COMPLETED Decision accepted, one edited (with a demoable diff between Raw output and the edited replacement), one rejected, one whose ruling was corrected (accept then reject — latest-wins visible in the replay thread), a TIMED_OUT and an ERRORED Decision, and a COMPLETED Decision still awaiting its first ruling.

Appends go through the MDR service layer — the same path production appends take — so the chain append, the audit-plane double-entry, and entry-id idempotency all fire exactly as they would for a real integration. Fixed entry ids make re-seeds append nothing new: a replayed append finds the existing Entry and returns it, which is what makes seeding an append-only table compatible with clean's leave-append-only-tables-alone posture (mdr_entries, like audit_events, is never deleted).

Dispositions carry the ops reviewer's identity as `human_id`. Correlation refs point at Sarah's seeded estate (her payment/account artifacts) so the ops pages can tell a joined-up story.

## Acceptance criteria

- [x] `seed` appends Model Decisions covering COMPLETED, TIMED_OUT, and ERRORED Outcomes, and ruling states ACCEPT, EDIT, REJECT, corrected-ruling (accept then reject), and awaiting-ruling
- [x] All appends go through the MDR service layer with fixed entry ids; a second `seed` run appends zero new MDR entries and zero new MDR audit events
- [x] Seeded Dispositions carry the ops reviewer's user id as `human_id`; Correlation refs reference Sarah's seeded estate
- [x] `clean` leaves `mdr_entries` and the `mdr` chain head untouched, documented alongside the audit_events posture
- [x] `verify-chain` exits 0 over both scopes after a full `reset` — asserted in the seeder-module test
- [x] Seeder-module test asserts the seeded worklist shape: entry counts per Outcome and ruling state

## Blocked by

- `09-ops-demo-admin-user.md` (the reviewer's identity is the `human_id` on every seeded Disposition)
