# 06 — The referred payment's Timeline: screening and its ruling in view

Status: ready-for-agent
Category: enhancement

## What to build

The subject the demo actually lands on. A payment that screening referred has a richer story than a settled one, and the records for it already exist: the payment row carries `screeningDecisionId`, and the audit plane carries `PAYMENT_REFERRED`, `MDR_DECISION_RECORDED`, `MDR_DISPOSITION_RECORDED`, `PAYMENT_RELEASED` and `PAYMENT_DECLINED` with their `afterState` summaries and trace ids.

Extend the Timeline assembly so a referred, released or declined payment brings the screening Decision and any rulings into view as Source records — sourced from the audit plane and the payment row, **not** by reading MDR internals, so the MDR stays ignorant of this consumer (ADR-0009). Extend the `copilot-payment/v1` template accordingly and bump its version; the hash changes with the bytes, which is the point.

The explanation for a referred payment should be able to say, with citations: the payment was screened, the decider flagged it, it was parked with funds still held, a human ruled, and the payment then released or declined — each claim citing the audit event or record it rests on.

## Acceptance criteria

- [x] A `REFERRED` payment's explanation cites the screening decision and the referral audit event, pinned by a happy acceptance test
- [x] A released payment's explanation cites the disposition and release events; a declined payment's cites the decline event
- [x] The hold's retention on a referred payment and its release on a declined one are citable claims
- [x] The template version and hash change with the template's bytes, and prior entries replay against their own pinned hash
- [x] No MDR read surface is added and no MDR internals are read — the screening story comes from audit events and the payment row
- [x] Grounding still validates against the assembled Timeline, with the new record types included in the retrieved-context refs
- [x] `mvn install` green

## Carried from 04

Two Timeline sources issue 04's prose named but its acceptance criteria did not, deliberately
left out of the tracer bullet and landing here:

- **The hold is a figure, not a Source record.** `LedgerClient` has no hold read at all — 04's
  Timeline carries `heldAmountMinorUnits` on the balances envelope, which satisfies 04's prompt
  criterion but cannot be cited. This issue's third AC ("the hold's retention ... are citable
  claims") therefore needs a new ledger read plus a `HOLD` value on `SourceRecordType` and on the
  `SourceRef.type` enum in `openapi.yaml`. Adding a response enum value is non-breaking.
- **The originating standing order or mandate is absent.** The link is a one-way hash: schedulers
  stamp `endToEndId = ScheduledPaymentEndToEndId.forOccurrence(instrumentId, occurrenceDate)`, so
  there is no column to join on. The forward path exists — `getActiveStandingOrders(token,
  accountId)` and `getActiveDirectDebits(token, accountId)` both take the debtor account, and each
  candidate instrument's occurrence id can be recomputed and matched against the payment's
  `endToEndId` — but it resolves *active* instruments only, so a cancelled instrument that caused a
  past payment stays invisible. Decide between that and a migration adding the reverse link before
  building it.

## Blocked by

- 05 (`05-failure-is-a-recorded-fact.md`)

## Resolution

The Timeline gains two Source record kinds and a second audit read. `SourceRecordType` grows `HOLD`
and `SCREENING_DECISION` between `PAYMENT` and `AUDIT_EVENT`, `LedgerClient` gains the hold read it
never had, and `PaymentTimelineAssembler` reads the audit plane twice — once against the payment,
once against the decision the payment row names. The template moves to `copilot-payment/v2` with the
new record types described in its own prose.

Found the implementation pre-staged in the working tree with no resolution note beside it, the same
shape issue 04 arrived in. Every claim below was established here rather than re-checked.

Decisions worth recording:

- **The hold's id is the payment's id**, which is the only link between the two — `PaymentsService`
  places every hold under `holdId = paymentId` and no column joins them. The assembler reads
  `GET /api/v1/internal/ledger/holds/{holdId}` at the payment's own id, and a 404 omits the record
  rather than failing the Explanation: a payment the ledger never held funds for is a real case
  (inbound, and anything that never reached the hold path), not an error. `debtorAccountId == null`
  short-circuits before the call is made at all.
- **The screening decision is a synthesised record, not a read.** Its `summary` carries the decision
  id plus `outcome` and `disposition` lifted from the audit plane's `afterState`, with `not recorded`
  / `not ruled` when the audit plane is silent. Nothing reads the MDR — pinned by
  `theScreeningStoryIsAssembledWithoutReadingTheMdrSoTheOnlyMdrCallIsTheCopilotsOwnAppend`, which
  asserts the single MDR call in the whole request is the Copilot's own append.
- **The ledger hold read is a 404, not a 400.** `LEDGER_HOLD_NOT_FOUND` already existed as a 400 on
  the mutation paths; a read of an unknown id is not an attempted mutation, so `HoldManager.get`
  throws `NotFoundException` on the same code. Same call issue 01 made for
  `LEDGER_TRANSACTION_NOT_FOUND`.
- **v1's template file is deleted, not kept.** Replay reads `prompt_template_hash` from the stored
  column (`MdrReadService`), never recomputing from a resource, so no prior entry depends on the
  bytes still being on disk. `AdminMdrDecisionsControllerTest` already pins that by reading a seeded
  hash back verbatim.

Fixed here, with a red test first, not merely reviewed:

- **The ruling and the release it caused could narrate in the wrong order.** Issue 04 fixed exactly
  this species of bug — audit events sharing a moment coming back in id order rather than chain
  order — but its fix only held *within* one `eventsFor` read. This issue adds a second read, and
  the two lists were appended in a fixed order (payment events, then the decision's) and stably
  sorted on `(moment, timelineRank)`. Both are `AUDIT_EVENT`, same rank, so a payment event always
  won a tie against an MDR event regardless of which actually happened. `ReferredPaymentResolver`
  records the disposition and then releases the payment; under `StubbedTime` those routinely share
  an instant. Proven red before the fix: `PAYMENT_RELEASED` (`…e4`) narrated *before* the
  `MDR_DISPOSITION_RECORDED` (`…e3`) that caused it — cause and effect reversed, in the demo's own
  story. The two reads are now merged and ordered by the chain's `seq`, which is a total order
  across resources where `ts` is not. Pinned by
  `aRulingAndTheReleaseItCausedShareAMomentAndAreNarratedInChainOrder`.

Proven, not asserted:

- **The audit join is real in production, not just in the stubs.** The acceptance tests write their
  own audit events, so a resourceType or key mismatch with the real writers would pass silently.
  Checked directly: `MdrDecisionService` and `MdrDispositionService` both write
  `resourceType = "ModelDecision"` with `resourceId = decisionId`, `afterState["outcome"]` and
  `afterState["disposition"]` — exactly what the assembler reads — and `PaymentsService:212` stamps
  the payment row's `screeningDecisionId` with the same `referral.decisionId` it puts on the
  `PAYMENT_REFERRED` event.
- **Both new record kinds were made to bite.** Two mutations, run then reverted: dropping the hold
  from the Timeline reddened 7 of 9 tests in the new class; dropping the screening decision reddened
  7 of 9. File restored byte-identical.
- **oasdiff run rather than claimed.** The exact CI invocation (`tufin/oasdiff:v1.10.6 breaking …
  --fail-on ERR --err-ignore ./malai-api/oasdiff-accepted-breaking-changes.txt`) exits 0: two
  `response-property-enum-value-added` **warnings** for `HOLD` and `SCREENING_DECISION` on
  `claims/items/sources/items/type`, zero errors. Same classification the exec-metrics `roles`
  widening got. `x-extensible-enum` deliberately not adopted — the generated TypeScript client's
  exhaustive union is the compile-time nudge the webapp wants. No allow-list line added.

**Carried from 04, resolved.** The hold is now a citable Source record, which is what that section
asked for. The originating standing order or mandate is **not built**, deliberately, and the
reasoning is recorded as an open question in `CONTEXT.md`: the forward path
(`getActiveStandingOrders` / `getActiveDirectDebits` + recomputing each candidate's occurrence id)
resolves *active* instruments only, so a cancelled instrument that caused a past payment stays
invisible — and a Timeline that carries the instrument sometimes and silently omits it other times
is worse than one that never offers it, because the reader cannot tell the two silences apart. The
honest fix is a reverse link on the payment row populated on both scheduler paths, which is a
write-path migration rather than a Timeline read.

Verified: `mvn install` BUILD SUCCESS across all 57 modules (56 + root), zero failures — copilot-service 64/64,
ledger-service 44/44, ledger client 16/16. Backend has no npm loop; the mvn run is its equivalent.

## Notes for next iteration

- **Issue 07's replay AC still needs the decision issue 03 flagged**, unchanged by this issue: it
  asserts `advisory = true` on the MDR replay route, which `MdrModelDecisionView` does not carry.
  Either add it there (additive, needs a one-line ADR-0013 amendment) or assert through the worklist
  summary, which carries it today.
- **The three e2e assertion fixes from issue 03 are still UNRUN** (`e2e.skip=true`). Issue 07 is the
  first thing that executes them; a summary-shape failure there is issue 03's correction being
  wrong, not 07's own regression.
- **`seq` ordering is now load-bearing across resources.** Any future Timeline source drawn from a
  third audit resource must join the same merge rather than being appended separately, or it
  re-opens the bug fixed here. The single global chain lock is what makes `seq` a total order — if
  audit is ever sharded per CLAUDE.md's note, this merge needs revisiting.
- **Nothing writes a copilot Timeline for a referred payment outside tests yet.** The seeded demo
  worklist has referred payments, so the demo beat works, but no e2e path exercises the screening
  branch until issue 07.
- **The webapp has no refusal state and no screening-story rendering.** `SourceRef.type` now carries
  two more values its generated union must learn.
