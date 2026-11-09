# 03 — Failure Outcomes: a missing model output is a recorded fact

Status: done

## Parent

`.scratch/mdr/PRD.md` (Model Decision Record)

## What to build

The same internal decisions endpoint accepts Model Decisions whose invocation concluded without output: Outcome TIMED_OUT (no Raw output) and ERRORED (error detail, no Raw output). Exactly one Entry per invocation regardless of Outcome, written on the independent transactional path so a rolled-back business transaction still leaves the record. This is the brief's core failure-semantics requirement made demoable on its own.

## Acceptance criteria

- [ ] A TIMED_OUT Model Decision is recorded with Rendered prompt present and Raw output absent; the chain links it exactly like a COMPLETED Entry
- [ ] An ERRORED Model Decision is recorded with error detail present
- [ ] Shape contradictions are rejected at both API and CHECK level: COMPLETED without Raw output, ERRORED without error detail, TIMED_OUT carrying an output
- [ ] The independent write path survives a caller rollback — the business transaction rolls back, the Entry remains (acceptance test)
- [ ] Full-entity database assertions across mixed-Outcome sequences; chain linkage asserted across them
- [ ] Sad-path tests per convention

## Blocked by

- `02-record-model-decision-tracer.md`

## Done (2026-07-18)

The write plane now records every Outcome, not just COMPLETED. No schema change — V25 already
shipped the disposition-side columns and the full CHECK set in slice 02, so this slice is purely
the writer/model/validation surface plus its tests.

- **Outcome shape enforced at two layers.** `RecordModelDecisionRequest` drops `@NotBlank` on
  `rawOutput`, gains a nullable `errorDetail`, and carries two `@get:AssertTrue` cross-field
  validators (`rawOutputMatchesOutcome`, `errorDetailMatchesOutcome`) — so a shape contradiction
  is a clean `400` (code `9999`, `MethodArgumentNotValidException`) at the API, never a `500` from
  the DB CHECK. The CHECK constraints from V25 are the defence-in-depth backstop, proven to fire
  when a caller bypasses the DTO (writer-level tests asserting `DataIntegrityViolationException`).
  Biconditionals: `COMPLETED ⇔ rawOutput present & errorDetail absent`; `ERRORED ⇔ errorDetail
  present & rawOutput absent`; `TIMED_OUT ⇔ both absent`.
- **`MdrEntryWriter.recordIndependently` (REQUIRES_NEW)** added beside `record` (REQUIRED),
  both delegating to a shared `appendChained` — mirrors `HashChainedAuditWriter` exactly. The
  writer now redacts `rawOutput` only when present and threads `errorDetail` into both the hash
  projection and the INSERT (the two hardcoded `null`s from slice 02 are gone). The service still
  writes decisions via `record()`; the independent path is offered and tested but not yet the
  default (no in-process business-tx caller exists this increment — the internal endpoint is
  already its own tx boundary from any HTTP caller).
- **`errorDetail` is NOT redacted** — the PRD's redaction list (#22) is exactly rendered prompt /
  raw output / verdict / edited output; error detail is a system-generated fact, hashed verbatim.

Verification (DB up, WireMock up):
  mdr-service     34/34 green — happy 6 (COMPLETED + new TIMED_OUT / ERRORED full-entity DB
                  assertions, mixed-Outcome 3-entry chain-linkage via in-test hash recompute +
                  prev/current chaining, redaction, idempotent replay), sad 4 (missing service
                  scope 401; the three API shape contradictions → 400/9999), writer 5
                  (recordIndependently survives caller rollback, record rolls back with it, and
                  the three CHECK-backstop rejections), architecture 19/19.
  malai-api       ReadyControllerTest boots the aggregator with the modified module; arch 7/7.

Notes for next iteration:
- Slice 05 (dispositions) is the next unblocked write-plane slice; 04 (verification surface) is
  also unblocked. 06 waits on 05; 07 waits on 04+05.
- Service→`recordIndependently` wiring is deliberately deferred until a real in-process consumer
  exists (would otherwise introduce a partial-failure audit-vs-decision double-entry gap on
  audit-write failure, buying nothing at demo scale).
