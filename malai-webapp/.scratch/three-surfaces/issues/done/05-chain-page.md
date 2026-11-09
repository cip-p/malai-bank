# 05 — Chain page: prove the record intact on demand

Status: ready-for-agent

## Parent

`.scratch/three-surfaces/PRD.md`

## What to build

The dedicated Chain page, joining the Ops nav beside the Worklist. A verify-now action triggers the on-demand MDR chain verification and renders the verdict for an executive watching over a shoulder: on success, the record is intact and how many rows were walked; on failure, which failure mode and the sequence number where the chain breaks, explained in plain language — what a gap, a broken predecessor link, or a recomputed-hash mismatch each mean about tampering. Idle, verifying, and verdict states are all designed; verification runs only when asked. This page proves the MDR scope's chain only — the audit plane's chain is a different scope and stays out.

## Acceptance criteria

- [ ] The Ops nav gains the Chain page; keyboard reachable like the rest of the shell
- [ ] Verify-now runs the MDR chain verification on demand and renders OK with rows-walked on the seeded environment
- [ ] Each failure mode renders its plain-language explanation with the breaking sequence number; page tests cover all verdict shapes
- [ ] Idle and in-flight states are designed, not blank
- [ ] Axe a11y spec covers the page (idle and verdict states) in both modes
- [ ] E2E journey completes: worklist → decision → ruling → chain verifies OK

## Blocked by

- 01-contract-refresh-surface-scaffold-and-worklist (parallel with 02+)
