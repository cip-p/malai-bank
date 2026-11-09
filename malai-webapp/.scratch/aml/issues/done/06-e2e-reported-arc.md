# 06 — E2E: the reported arc

Status: ready-for-agent

## Parent

`.scratch/aml/PRD.md`

## What to build

The full-loop proof in the browser, as a spec in the default e2e project (the evaluator holds ADMIN; structuring evidence is sub-threshold by design, so no screening-mode prerequisite — unlike the screening and copilot specs, this one never skips). This slice also builds the shared machinery the refusal arc will reuse: sandbox top-up, firing a structuring pattern (three £3,400 payments to a per-run-unique beneficiary), and waiting for the Case to appear within a sweep tick — the polling proving itself.

The arc: fire the pattern → the Case list grows a row without a manual refresh → open the Case view, find the Alert with its typology and evidence → accept it at its Decision view → back on the Case view, arm and fire close-dismissed and witness the accepted-alert-stands refusal rendered as a fact → request the SAR draft and read the narrative inline → accept the draft at its Decision view → close as reported → the Case reads CLOSED_REPORTED with the attached narrative verbatim.

Self-healing by design: the terminal close clears the one-open-case-per-account slate for the next run; the per-run-unique beneficiary keeps evidence sets from colliding across runs; the spec tolerates stale alerts absorbed from a prior failed run by working with its own alert and closing regardless. Amounts dodge the armed values (per-payment £1,732.05 and £2,236.06; evidence £3,141.59, £5,772.15, £6,931.47).

## Acceptance criteria

- [ ] The spec runs in the default e2e project and never self-skips
- [ ] The fired pattern lands as a Case list row within a sweep tick with no manual refresh step in the spec
- [ ] The Alert is accepted through its Decision view, and the dismiss refusal is asserted as the rendered fact
- [ ] The drafted narrative is asserted inline, the draft is accepted at its Decision view, and the close lands CLOSED_REPORTED with the attached narrative asserted
- [ ] The spec is self-healing across runs: unique beneficiary, terminal close, tolerance of stale alerts from a prior failed run
- [ ] The shared firing/top-up/waiting helpers are reusable by the refusal-arc spec

## Blocked by

- `03-worklist-speaks-aml-case-correlation-link.md`
- `04-sar-panel.md`
- `05-close-actions.md`
