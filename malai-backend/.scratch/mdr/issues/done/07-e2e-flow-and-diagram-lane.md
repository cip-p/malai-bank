# 07 — E2E flow and diagram lane: decision → disposition → chain verified

Status: ready-for-agent

## Parent

`.scratch/mdr/PRD.md` (Model Decision Record)

## What to build

The brief's named end-to-end flow, recorded as a sequence diagram with the MDR as its own party lane. The test-support no-op decider (deterministic, controllable Outcomes, zero provider dependency) writes a Model Decision through the internal endpoint, an ADMIN attaches a Human Disposition, and chain verification returns Ok. Requires the lockstep diagram wiring: the party singleton, the envelope source-name mapping, the from/to system lookups, the HTTP classifier entries, and the service prefix — no SQS classifier, the MDR owns no queues. Miss one and the from/to request assertions silently fail; wiring all of them is part of the slice's proof.

## Acceptance criteria

- [ ] MDR party lane fully wired: party singleton, envelope source mapping, from-system and to-system lookups, HTTP classifier arrow labels, service prefix
- [ ] E2E helper interface and REST client wiring in the e2e context; the no-op decider drives the internal write endpoint deterministically
- [ ] E2E test: no-op decider records a Model Decision → ADMIN appends a Human Disposition → the MDR chain-verification endpoint returns Ok — with database assertions on the chained rows
- [ ] The from/to captured-request assertions pass with the MDR as its own party, proving the lane wiring is complete
- [ ] The Kensa sequence diagram renders the flow with the MDR on its own lane; the e2e suite passes with e2e enabled

## Blocked by

- `04-chain-verification-surface.md`
- `05-human-dispositions.md`
