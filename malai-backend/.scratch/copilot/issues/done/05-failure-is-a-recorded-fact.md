# 05 — Failure is a recorded fact: refusal, timeout, error

Status: ready-for-agent
Category: enhancement

## What to build

The four ways an explanation can fail to arrive, each one a record.

**Grounding refusal** (ADR-0012). A well-formed answer citing a record outside the Timeline fails whole: outcome `COMPLETED`, raw output stored verbatim, **Verdict absent**, response carries the decision id and no claims plus a domain code the surface can render as "an answer was produced but failed grounding validation". Same treatment for a claim citing nothing at all. The raw output stays inspectable on the MDR entry — refused for display, never hidden from the record.

**Armed subjects.** Two property-configured triggers on the deterministic decider, mirroring screening's armed amount: one subject shape makes it exceed the timeout budget, one makes it emit a claim citing a fabricated id. Both deterministic, both demoable on cue, neither requiring a provider. Pick shapes that cannot collide with seeded demo payments and document them in the issue's resolution note for the demo script.

**Timeout and error.** A decider exceeding the property-tunable budget appends a `TIMED_OUT` entry; any other decider failure appends `ERRORED` with `errorDetail`. Both return 201 with the decision id, the outcome, and no claims — the invocation concluded, the record exists, there is nothing to read.

**MDR write failure.** Fail-closed, exactly as screening: if the Entry cannot be appended, the request fails and no explanation is served. Nothing a human can read may exist off-chain.

## Acceptance criteria

- [x] The armed ungrounded subject produces a `COMPLETED` entry with raw output stored and verdict absent, and a response carrying no claims and the refusal code — happy-path acceptance test
- [x] A claim citing nothing is treated identically to a claim citing outside the Timeline
- [x] The armed timeout subject appends `TIMED_OUT` with no raw output; a decider error appends `ERRORED` with `errorDetail`; both respond 201 with the decision id and no claims
- [x] An MDR write failure fails the request and serves no explanation — sad acceptance test
- [x] The grounding validator has direct tests: cites outside the Timeline, cites nothing, empty claim list, duplicate citations, all-valid
- [x] The refused answer remains readable as raw output on the MDR decision replay — by composition, verified rather than asserted (see the resolution)
- [x] `mvn install` green

## Blocked by

- 04 (`04-tracer-a-cited-explanation-for-a-settled-payment.md`)

## Resolution

`ExplanationService` now takes the shape `PaymentScreener` already had: the Decider call sits in a
try, the outcome is what came back from it, and exactly one Model Decision is appended whichever way
it went. Three armed amounts on the deterministic decider make each failure reachable on cue, and a
`DeciderBudget` gives the seam a real time bound rather than a simulated one.

Decisions worth recording:

- **A refusal is a code on a 201, not an error.** `ExplanationResponse` gains
  `refusalCode: GROUNDING_FAILED`, present only when an answer was produced and withheld. It is
  absent on a served Explanation and absent under `TIMED_OUT`/`ERRORED`, where nothing was produced
  to refuse — which is precisely the distinction `outcome` cannot draw on its own, since a refusal
  is a `COMPLETED` invocation. A named enum rather than a `MalaiCode`: the numeric code table is the
  *error-body* vocabulary (`{"message":"8975"}`), and this invocation did not fail. Every other
  non-error enum in the contract is a name, and a name is what the webapp's generated client wants.
- **The armed subjects are amounts, mirroring screening's armed amount** — the one property of a
  subject a demo presenter can choose at will, since a payment id is minted by the platform. Three
  mathematical constants beside screening's π: **£2,718.28 (271_828) times out**, **£1,618.03
  (161_803) fabricates a citation**, **£1,414.21 (141_421) errors**. None collides with screening's
  314_159; none reaches screening's £10,000 threshold, so an armed copilot subject is not also an
  armed screening subject; and no seeded demo payment uses them. Only the first two are demo beats —
  the third exists so `ERRORED` is reachable at the acceptance level rather than only through a
  stubbed decider.
- **The Timeline carries `subjectAmountMinorUnits`.** The Decider is shown the amount already (it is
  in the rendered prompt), but only inside the payment record's summary string; arming on it
  otherwise would mean parsing prose. The field makes the subject's amount addressable without
  making the Timeline any less "the ordered set of Source records".
- **The budget is enforced, not simulated.** `DeciderBudget.within { }` runs the Decider on its own
  thread and abandons it at `malai.copilot.decider-timeout-ms`, turning a breach into
  `CopilotTimedOutException` — the same vocabulary a Decider that self-reports a timeout throws, so
  `ExplanationService` has one timeout branch, not two. The armed timeout subject *sleeps past* the
  budget rather than throwing, so the test exercises the real bound; issue 08's provider inherits a
  budget that has been proven to bite rather than one that has only ever been simulated.
- **The executor is owned by `DeciderBudget`, not published as a bean.** `malai-api` bundles this
  context beside exec-service, whose `MetricFanOut` injects an `ExecutorService` **by type** with a
  parameter name (`executor`) that matches no bean name. A second candidate of that type makes that
  injection ambiguous and the aggregator fails to boot — verified by adding the bean and watching
  `MalaiApiApplicationTest` fail with `NoUniqueBeanDefinitionException`, then reverting. The private
  cached pool costs nothing and keeps the blast radius at zero.
- **`CopilotProperties` defaults keep the property blast radius at zero for the armed amounts**,
  exactly as `ScreeningProperties.armedTimeoutAmountMinorUnits` is declared in no properties file
  anywhere. The budget is the exception and *is* declared in copilot-service and `malai-api` as
  `${MAL_COPILOT_DECIDER_TIMEOUT_MS:10000}`, because a deployer wiring a real provider must be able
  to tune it without a rebuild. Tests override it to 200ms, which is what keeps the timeout test
  fast.
- **The MDR write failure needed no production change.** The client already throws and nothing
  catches it, so the request fails and no explanation is served; the sad test pins that (500, and a
  body carrying neither `claims` nor `decisionId`), matching `ScreeningPaymentsControllerTest`'s
  fail-closed precedent rather than inventing a second one.
- **The refused answer's readability on replay is proven by composition, not by a duplicated test.**
  This issue pins that the copilot writes `rawOutput` with `verdict` absent; mdr-service already
  pins that exactly that row replays with its `rawOutput` rendered — `WithMdrEntries` writes every
  fixture with `verdict = null`, and `AdminMdrDecisionsControllerTest` asserts `"rawOutput" To
  "APPROVE: low risk."` on the replay body. `MdrModelDecisionView` carries `rawOutput` and `verdict`
  as independent nullable fields, so there is no path by which a missing verdict hides an output.
  Issue 07 does the live round trip.
- **A sixth grounding test beyond the five the AC names**: a citation of the right id on the wrong
  surface. `SourceRef` is a `(type, id)` pair and the Timeline admits pairs, so a ledger entry id
  cited as an audit event is ungrounded — worth pinning, because it is the failure a real provider
  is most likely to produce by accident.

Proven in this pass, not inherited:

- **`DeciderBudget` had no direct test and now has eight.** It was the one new production class
  carrying real concurrency semantics — abandonment, cause-unwrapping, thread-context propagation —
  and every one of those was reachable only through a single armed acceptance test, which cannot
  distinguish *why* it passed. `GroundingValidator` got direct tests in the same slice; this seam is
  the harder of the two and had none. `DeciderBudgetTest` pins: an answer within budget returns; an
  overrun becomes `CopilotTimedOutException` naming the budget; the abandoned decider is genuinely
  interrupted rather than left running behind the answer; a decider's own exception surfaces instead
  of `ExecutionException`; a self-reported `CopilotTimedOutException` keeps that vocabulary rather
  than degrading to `ERRORED`; the decider runs off the caller's thread; it carries the caller's
  `MalaiSource` and `TestId`; and the pooled thread is left clean for the invocation after it.
- **Each of those tests was made to bite before being trusted**, the same discipline issue 02 used on
  its ArchUnit rule. Four mutations of `DeciderBudget`, each run and then reverted: dropping the
  `finally { unset() }` reddened only the pooled-thread-hygiene test; dropping the context
  propagation reddened only the attribution test; replacing `throw ex.cause ?: ex` with `throw ex`
  reddened both unwrap-dependent tests and nothing else; deleting `pending.cancel(true)` reddened
  only the interruption test. The file was restored byte-identical afterwards.
- **The context propagation was the reason to write these.** Nothing else in the repo exercises it,
  and it is load-bearing twice over: issue 08's provider makes real outbound calls from that thread,
  and per the Kensa lane rules a call leaving without its `MalaiSource` lands on the wrong party —
  which breaks `theFollowingHttpRequestWasMade` assertions rather than failing visibly here. A leaked
  `TestId` on a pooled thread would misattribute a later test's WireMock stubs, the same class of
  cross-test bleed the shared `RestClient`'s cookie handling already cost this repo once.
- **oasdiff was run, which the staged note did not claim.** The exact CI invocation
  (`tufin/oasdiff:v1.10.6 breaking … --fail-on ERR --err-ignore
  ./malai-api/oasdiff-accepted-breaking-changes.txt`) against `HEAD`'s contract exits 0; the
  changelog names exactly one info change, `response-optional-property-added` for `refusalCode` on
  the 201. `required` stays `[decisionId, outcome, claims]`, so the new field is optional in the
  schema as well as in the prose. No suppression line was needed or added.
- **The four MDR request shapes the Copilot can now emit were checked against the MDR's own rules**,
  which matters because copilot-service stubs MDR through WireMock and so cannot catch a rejection
  itself. `RecordModelDecisionRequest` asserts `rawOutput` present iff `COMPLETED` and `errorDetail`
  present iff `ERRORED`; V25 carries the same three as CHECK constraints. Grounded-completed,
  refused-completed, timed-out and errored all satisfy both layers, and `MdrEntryWriterTest` already
  pins the rejections of the shapes just outside them.
- **The replay AC is satisfied by composition, and the composition was read rather than assumed.**
  `WithMdrEntries` writes every fixture with `verdict = null`, and `AdminMdrDecisionsControllerTest`
  asserts `"rawOutput" To "APPROVE: low risk."` on the replay body of exactly that row shape — which
  is the refusal shape. Issue 07 does the live round trip.
- **`subjectAmountMinorUnits` does not reach the prompt**, so `promptTemplateHash` is unchanged and
  issue 04's rendered-prompt assertions stay green: `CopilotPaymentPrompt.render` substitutes only
  `subjectType`, `subjectId`, `balances` and the record lines.
- **`refusalCode = null` serialises as an absent key**, so issue 04's `noExtraProps()` assertions on
  the served Explanation are untouched — `spring.jackson.default-property-inclusion=non_null` is set
  in both copilot-service and `malai-api`. The same holds on the MDR request, whose client owns a
  private `NON_NULL` mapper, which is what makes the tests' `has("rawOutput") shouldBe false`
  assertions mean absence rather than a JSON null.
- **The fail-closed sad test fails for the right reason.** `anInternalPaymentResponse()` defaults
  `traceId` and `debtorAccountId` to null, so the assembler makes no ledger or balances call at all
  and the only downstream left to fail is the MDR write. The 500 is the MDR write failing, not an
  unstubbed dependency.

Demo script, for the presenter:

| Send this amount | The Copilot then |
| --- | --- |
| £2,718.28 | records `TIMED_OUT` — a slow model is a recorded fact, and the request still answered |
| £1,618.03 | produces an answer citing a ledger entry that was never retrieved, records it whole, and refuses to render it |
| £1,414.21 | records `ERRORED` with the failure detail |

Each is a payment sent for that amount, then explained through
`POST /api/v1/admin/copilot/explanations`. The screening armed amount (£3,141.59) is untouched and
still times out screening, so the two demos do not interfere.

## Notes for the next iteration

- **Nothing sets `TIMED_OUT`/`ERRORED` outside the armed amounts**, by construction — the
  deterministic decider cannot fail any other way. That is the point, and it is also why issue 08's
  adapter is the first code that can produce these outcomes for a real reason.
- **`CopilotTimedOutException` now has a thrower** (`DeciderBudget`), where issue 04 left it
  declared and unused. A provider adapter that surfaces its own read timeout should throw it too
  rather than adding a second timeout type.
- **Issue 06 inherits the refusal shape unchanged.** Its new Source record types widen the Timeline
  the same validator checks against, so nothing here needs revisiting — but its template bump
  changes `promptTemplateHash`, which the armed tests do not assert on.
- **The webapp has no refusal state yet.** The backend now distinguishes refused from never-arrived;
  rendering that distinction is `malai-webapp`'s move, and `refusalCode` is the field to switch on.
