# 03 — Ruling draft: accept / edit / reject at the foot of the thread

Status: ready-for-agent

## Parent

`.scratch/three-surfaces/PRD.md`

## What to build

The append affordance, rendered where the next Entry will chain: at the foot of the disposition thread. Opening the draft mints the Entry id client-side and holds it for the draft's life — a retried submit re-sends the same id, and a replayed append returns the existing Entry, so retries can never fork the record. Accept and reject carry the ruling alone; edit requires the full replacement output (never a patch), prefilled from the raw output so the reviewer edits the whole artefact. Submitting appends the Disposition and refetches the thread and worklist; the new Entry appears in the thread where the draft stood. Failures render inline in the draft (the mutation is handled locally — no generic toast). All copy is append-only: the action is "append a ruling", nothing "updates" or "saves".

A dedicated hook owns the draft lifecycle (id minting and stability, per-kind payload rules, invalidation); the draft UI consumes it.

## Acceptance criteria

- [ ] Draft opens at the thread's foot with accept / edit / reject; edit exposes the full-replacement editor prefilled with the raw output
- [ ] Entry id is minted at draft-open and unchanged across a failed submit and retry; hook tests prove id stability and that edit-and-only-edit carries the replacement output
- [ ] Successful append renders the new Disposition in the thread and updates the worklist's Ruling in force without a manual refresh
- [ ] Submit failure renders inline in the draft; no generic error toast fires
- [ ] No user-facing copy on the Ops surface says update / edit-in-place / save for rulings
- [ ] Page tests cover draft open/submit/failure states and the append-only copy
- [ ] Axe a11y spec covers the Decision view with the draft open, in both modes
- [ ] E2E: the reviewer rules on a seeded awaiting-ruling decision; the thread grows and the worklist row leaves the awaiting filter

## Blocked by

- 02-decision-view-the-record
