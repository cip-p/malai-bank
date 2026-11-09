# 05 — The Close actions

Status: ready-for-agent

## Parent

`.scratch/aml/PRD.md`

## What to build

The two terminal officer actions on the Case view, per ADR-0022. Close-as-dismissed and close-as-reported are offered on every non-terminal Case and absent — not disabled — on terminal ones, and never pre-disabled by client prediction: the Gate decides, the UI displays. Each action uses two-press inline confirm — the first press arms it (and mints the idempotency key, which survives retries), the second fires, clicking elsewhere disarms — no modal.

A 409 refusal renders as a designed inline fact whose copy is mapped from the domain code by a pure refusal-mapping module: accepted-alert-stands (dismiss), no-SAR-ruling-in-force (report), already-closed (either); unknown codes fall back generic. Never a toast — the refusal is the feature. A successful close refetches the case so the terminal state, and for reported the attached narrative, render immediately.

Demoable alone: on a live or seeded open Case, arm and fire close-reported with no SAR ruling and watch the refusal render as a fact; reject every alert and close dismissed.

## Acceptance criteria

- [ ] Both closes are offered on every non-terminal Case and absent on terminal ones
- [ ] First press arms and mints the idempotency key, second press fires, clicking elsewhere disarms; the armed key survives a retry
- [ ] Each of the three domain-coded refusals renders its designed inline fact; an unknown code falls back generic; nothing toasts
- [ ] Neither action is ever disabled by client-side prediction of a refusal
- [ ] A successful close renders the terminal Case state, including the attached narrative on close-reported
- [ ] The refusal mapping and the two-press arm logic are pure modules tested in isolation
- [ ] Colocated unit tests per house style; lint, prettier, and the unit suite green

## Blocked by

- `02-case-view-assembly.md`
