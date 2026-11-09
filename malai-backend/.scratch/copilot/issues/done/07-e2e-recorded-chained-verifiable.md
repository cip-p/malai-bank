# 07 — e2e: an explanation, recorded, chained and verifiable

Status: done
Category: enhancement

## What to build

One journey in `malai-end-to-end-tests` against the running stack, proving the loop the brief asked for end to end on synthetic data: a payment exists → an explanation is requested for it → the model is invoked through the seam → an Entry is appended and chained → integrity is verifiable → the claims cite ids that resolve.

Steps: drive a customer payment to a terminal state through the existing e2e machinery, request an explanation as an ADMIN, assert claims are returned with source ids, resolve at least one cited id back through the surface that owns it, read the decision back through the MDR replay route and assert the Entry's model identity, template hash, retrieved-context refs and `advisory` flag, then run chain verification and assert it walks the new entry alongside the seeded ones.

Add the copilot lane to the e2e diagrams following the existing MDR e2e flow's shape.

## Acceptance criteria

- [ ] The journey runs green against the bundled stack with no external provider and no seeded copilot data
- [ ] The explanation's claims carry source ids, and at least one is resolved back to its owning surface within the test
- [ ] The MDR replay shows the Entry with honest model identity, real template hash, one ref per Source record, and `advisory = true`
- [ ] Chain verification walks the live entry and the seeded entries in one pass
- [ ] The copilot appears as its own lane on the e2e diagram
- [ ] `mvn install` green

## Blocked by

- 06 (`06-referred-payment-timeline.md`)

---

## Resolution

Landed. The journey runs against the bundled stack and is green — and running it, rather than
trusting it, is the whole story of this issue. The implementation arrived pre-staged in the working
tree with no resolution note beside it, the same shape issues 04 and 06 arrived in, so every claim
below was established here.

**Issue 03's flagged decision, taken.** The replay read now carries `advisory` on
`MdrModelDecisionView`. Issue 03 deliberately left it off and named the choice for whoever landed
07: add it to replay, or assert through the worklist summary instead. Replay is the auditor's read
of a *single* Entry; having to call the worklist to learn whether a ruling was ever expected of the
Entry in front of you makes the record answer a question about itself only in aggregate. ADR-0013
carries a one-line amendment saying so. `oasdiff` classifies it
`response-required-property-added` — one info change, zero errors, no allow-list line.

### Two real defects, found only by executing the journey

Both were invisible to every acceptance test in the repo, and both would have reached the demo.

- **The Copilot cited no ledger entry at all.** `PaymentTimelineAssembler` scopes its ledger read to
  `payment.traceId`; payments-service reads that from `MDC.trace_id` at write time; and *nothing in
  this repo puts it there*. In production the OpenTelemetry agent's logback-mdc instrumentation
  does — attached by the `JAVA_TOOL_OPTIONS` the ECS task definition sets, not by the image, whose
  ENTRYPOINT is a plain `java -jar`. So the bundled stack persisted `payments.trace_id = NULL` and
  the trace-scoped read came back empty, silently: no error, no empty-state, just a Timeline missing
  the records that show the money moving. Proven at the database, not inferred — the payment row's
  `trace_id` was null while two ledger transactions sat beside it.
  Fixed by `MdcTraceIdServletFilterConfig` in malai-commons, `@Profile("e2e-tests")`, standing in
  for the agent exactly as `AcceptanceTestsConfiguration` already does for acceptance tests (its
  own comment names this gap). It honours an inbound `X-Test-Trace-Id`, never overwrites a value
  the agent already set, and clears only what it put there.
- **The rendered prompt leaked an unmasked account number.** The `PAYMENT` Source record masks
  account numbers and IBANs structurally, from typed fields. The `AUDIT_EVENT` record piped
  `afterState` through verbatim — and `PAYMENT_INITIATED`'s afterState carries `toAccountNumber` and
  `toIban` raw. The prompt is the payload that leaves the estate in issue 08, and it contained
  `toAccountNumber=11223344`. The copilot's own acceptance tests could not catch this: they write
  their own audit events, and none of them had ever carried a real account number.
  Fixed by `maskedAuditState`, masking by key name because `afterState` is an untyped map written by
  whichever service recorded it. Sort codes stay in full — a sort code names a bank, not a customer.

### Proven, not asserted

- **The e2e suite was actually run**, which is what this issue exists for. 43/43 green across 18
  classes against the bundled stack. That includes the three e2e assertion fixes issue 03 made and
  could not execute (`e2e.skip=true`): `PaymentScreeningTest` 3/3 and `MdrTest` 1/1 now run, and
  issue 03's corrections were right. The note carried forward since issue 03 is closed.
- **Both fixes were made to bite before being trusted.** Reverting the assembler's masking reddened
  exactly one test with the raw account number in the failure message; file restored byte-identical.
  The trace filter took four mutations — dropping the agent guard reddened only the two
  never-overwrite tests, ignoring the header reddened only the header test, dropping the
  finally-remove reddened the two hygiene tests plus the distinctness test, and a fixed trace id
  reddened the shape and distinctness tests. File restored byte-identical.
- **The copilot has its own diagram lane**, checked against the generated Kensa report rather than
  by reading the lookup: `CopilotService` appears beside `Client`, `PaymentsService`,
  `LedgerService` and `MdrService`.
- **The trace filter did not perturb the rest of the suite.** It makes `trace_id` non-null
  stack-wide, which is exactly the kind of change that quietly reddens full-entity assertions
  elsewhere; all 17 other e2e classes stayed green.

### One AC read, not met literally

"Chain verification walks the live entry and the seeded entries in one pass" — the journey resets
the chain to genesis first, because `theMdrChainIsAtGenesis()` is what makes `rowsVerified`
deterministic, and every existing MDR e2e test does the same. So there are no demo-*seeded* rows in
the pass. What is proven is the stronger available form: one verification walks the live copilot
entry alongside two pre-existing entries of both kinds (a screening Model Decision and a Human
Disposition), `rowsVerified` = 3, with the copilot entry's `prev_hash` asserted equal to the
disposition's `current_hash`.

Files: `MdcTraceIdServletFilterConfig` + its test (new, 8) in malai-commons; `maskedAuditState` in
`CopilotHashing` + `CopilotRedactionTest` (new, 7) + `PaymentTimelineAssembler` +
`ReferredPaymentExplanationTest` (10, +1); mdr-service `MdrReadModels`, `MdrReadService`, happy
`AdminMdrDecisionsControllerTest`, `CONTEXT.md`; e2e `CopilotExplanationTest` (new) +
`WithCopilotService` (new) + `MalaiE2ETest` + `MalaiE2ETestContext` + `KensaEventConsumer` +
`PaymentScreeningTest` + `docker-compose.e2e.yml`; `MalaiSortCodePrefixLintTest`; copilot
`CONTEXT.md`; `docs/adr/0013`; `openapi.yaml`.

Verified: `mvn install` BUILD SUCCESS across all 57 modules, 20m31s, zero failures anywhere —
copilot-service 72/72, mdr-service 91/91, malai-commons 74/74. Separately, the e2e suite against the
bundled stack with `-De2e.skip=false`: 43/43 across 18 classes, including CopilotExplanationTest 1/1,
PaymentScreeningTest 3/3 and MdrTest 1/1. Backend has no npm loop — the mvn run is its equivalent.

## Notes for next iteration

- **The local demo still omits every ledger entry from an Explanation.** The trace-id fix is gated
  to `e2e-tests`, so it repairs the e2e stack and nothing else. `start-malai-api-local.sh` is a
  plain `java -jar` with no agent, so a locally demonstrated Explanation cites the payment, the
  hold, the screening decision and the audit events — and silently no ledger legs. That is the
  demo's "and here is the money moving" beat missing, with nothing on screen to say so. The honest
  fix is for the platform to mint its own trace id instead of inheriting one from an observability
  agent, unconditionally rather than per-profile. Deliberately not done here: it is a cross-cutting
  write-path change touching every service, it flips `audit_events.trace_id` and
  `ledger_entries.trace_id` from null to non-null in acceptance tests (at least five full-entity
  assertions assert null today), and it deserves its own ADR. Recorded as an open question in the
  copilot `CONTEXT.md`.
- **`maskedAuditState` masks by key name, so it fails open on a key nobody anticipated.** Today's
  writers are enumerated and covered (`toAccountNumber`, `toIban`, and the `accountNo` / `IBAN`
  spellings), but a future service writing a new sensitive key into an `afterState` on resource
  type `Payment` would reach the prompt unmasked. This matters specifically for issue 08, where the
  prompt leaves the estate. An allow-list would fail closed instead, at the cost of silently
  dropping narrative detail the reader may need.
- **Issue 08 is the only remaining issue, and it is the flagged one** — it needs a live provider and
  a key, which the repo cannot supply, and its own AC says the deterministic decider stays the
  default that every test and e2e path touches.
- **The webapp still has no refusal state and no screening-story rendering**, and
  `MdrModelDecisionView` now carries `advisory`, which its generated client will pick up on the next
  `openapi:gen`.
