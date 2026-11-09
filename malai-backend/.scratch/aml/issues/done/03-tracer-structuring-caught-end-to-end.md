# 03 — Tracer: structuring caught end-to-end

Status: ready-for-agent
Category: enhancement

## Parent

`.scratch/aml/PRD.md`

## What to build

The tracer bullet: five sub-threshold payments become a case on the worklist, with nothing hidden behind stubs.

A ShedLock-coordinated sweep (~10s) statelessly re-evaluates rolling windows ending now over settled payments, pulled through the new payments client method. The `AmlMonitoringDecider` seam fronts a deterministic `RulesAmlDecider` — model id `malai-aml-rules` version 1, rendered prompt template `aml-monitoring` v1 — carrying the structuring rule only: N outbound payments to the same beneficiary within the window, individually under the screening threshold, jointly at or above it. All parameters configurable under the `malai.aml.*` prefix.

A hit records one non-advisory Model Decision via the MDR client — correlation ref ACCOUNT, verdict carrying typology, risk score, and content-hashed evidence refs (the citation-whitelist pattern) — deduplicated on the hash of typology plus sorted evidence payment ids, so overlapping windows raise once. Recording the alert atomically opens the account's case (one open case per account) with an `AML_CASE_OPENED` audit event. The worklist endpoint from the published contract returns it.

Recorded absence holds: a decider failure over a candidate pattern records its own `ERRORED`/`TIMED_OUT` Outcome. Quiet sweeps and dedup-suppressed re-detections record nothing.

## Acceptance criteria

- [ ] Isolated `RulesAmlDecider` tests: N−1 payments don't fire, threshold-exact sums do, window-edge timing, beneficiary identity via sort code + account number and via IBAN
- [ ] Acceptance: pattern raises exactly one alert across repeated sweeps; verdict, evidence refs, and honest model naming asserted strictly
- [ ] Acceptance: case auto-opened, full-entity database assertion, `AML_CASE_OPENED` audit event asserted
- [ ] Acceptance: second alert for the same account joins the existing open case rather than opening a second
- [ ] Acceptance: decider failure records `ERRORED`/`TIMED_OUT`; quiet sweep records nothing
- [ ] Worklist endpoint matches the contract with strict payload assertions
- [ ] `mvn install` green

## Blocked by

- `01-payments-settled-window-read.md`
- `02-aml-contract-module-bootstrap-adr.md`
