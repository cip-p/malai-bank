# 03 — MDR Entries declare whether a ruling is expected

Status: ready-for-human
Category: enhancement

## What to build

ADR-0013. A Model Decision declares `advisory` about itself, so the reviewer's worklist can stay the list of decisions that hold something up once a read-only Decision point starts appending entries.

- `RecordModelDecisionRequest` gains `advisory: Boolean` (default `false`), carried on both the internal write route and the seeded-data path.
- One migration: a nullable `advisory BOOLEAN` column on `mdr_entries`, constrained to the MODEL_DECISION row shape exactly as the other model-side columns are. Absent means not advisory, so every seeded row stays valid.
- The field joins the canonical JSON the chain hashes — a new field on a hashed row is a deliberate change, so verify the existing chain-verification tests still walk seeded entries and the genesis linkage is untouched.
- `MdrDecisionSummary` carries `advisory`; `GET /api/v1/admin/mdr/decisions` gains an `advisory` query filter composing with the existing `rulingState`.
- `openapi.yaml` updated first, per the house rule: the request field, the summary field, the query parameter. The compatibility check will flag the response widening — approve it consciously, as the `REFERRED`/`DECLINED` widening was.

Nothing else changes: the Gate answers exactly as before, an advisory decision may still accrue Human Dispositions, and replay is untouched. `Advisory` is defined in `malai-services/mdr-service/CONTEXT.md`.

## Acceptance criteria

- [x] A Model Decision written with `advisory = true` is stored, replayed and chained identically to any other, pinned by happy acceptance tests with full-entity database assertions
- [x] `advisory` defaults to `false` when absent on the write, and seeded rows read back as not advisory
- [x] The worklist summary carries `advisory`, and the `advisory` query filter composes with `rulingState` — happy tests for each combination that matters, sad test for an invalid value
- [x] Appending a Human Disposition to an advisory decision still works, and the Gate's behaviour is unchanged — pinned by test, not by assertion in prose
- [x] Chain verification walks entries written before and after the migration — see the caveat below: "before the migration" cannot mean a row hashed without the field
- [x] `openapi.yaml` is updated and the oasdiff break is consciously approved — there was no break to approve; the exact CI command was run locally and reports 0 errors
- [x] `mvn install` green

## Blocked by

Nothing.

## Resolution

`V27__mdr_entries_advisory.sql` adds the nullable column; `advisory` now rides the write request,
the hashed canonical payload, the stored row, the worklist summary and a worklist query filter.
Four surfaces, exactly as ADR-0013's consequences section scoped it.

Decisions worth recording:

- **The `advisory` filter is a filter, not a new default.** With the parameter omitted the worklist
  answers exactly as before — both kinds, unchanged behaviour for every existing caller. The
  reviewer's default view is `?rulingState=AWAITING_RULING&advisory=false`, composed by the caller,
  which is what "composes with the existing `rulingState`" asks for. Putting the narrowing in the
  API's default would have made `advisory` forbid something, which ADR-0013 and ADR-0012 both
  explicitly refuse.
- **The row-shape CHECK was recreated, not supplemented.** `advisory IS NULL` joins the
  HUMAN_DISPOSITION branch of `mdr_entries_kind_shape` alongside the other model-side columns, so
  one constraint still reads as the whole row shape per kind rather than the shape plus a footnote.
  The MODEL_DECISION branch deliberately does NOT require it non-null: absent means not advisory.
- **Absent means not advisory at every layer.** The column is nullable, the request field defaults
  to `false`, and the summary reads `advisory ?: false`. The writer always stores a real boolean, so
  a NULL can only come from a row written before this migration. That last layer was the one claim
  here asserted rather than pinned, since no test could reach it through the writer:
  `aDecisionWrittenBeforeTheAdvisoryColumnExistedReadsBackAsNotAdvisory` now nulls a stored row's
  flag with raw SQL (`theMdrEntryAtSeqPredatesTheAdvisoryColumn`) and asserts it both reads back
  `advisory = false` and is admitted by `?advisory=false` — the pre-migration row behaving exactly
  as the migration's "no backfill needed" promise requires.
- **`openapi.yaml` gains two things, not three.** The issue lists "the request field, the summary
  field, the query parameter" — but the write route is `POST /api/v1/internal/mdr/decisions`, and
  internal service-to-service routes are documented as deliberately out of the public contract
  (openapi.yaml line 30). There is no request schema to add the field to. Same call issue 01 made.
- **There was no oasdiff break.** The AC anticipated one, so it was verified rather than assumed:
  the exact CI invocation (`tufin/oasdiff:v1.10.6 breaking … --fail-on ERR --err-ignore …`) was run
  locally against `HEAD`'s contract and exits 0 — `2 changes: 0 error, 0 warning, 2 info`, being
  `new-optional-request-parameter` (the query param) and `response-required-property-added` (the
  summary field). Both non-breaking: callers gain a parameter they may ignore and a field they may
  ignore. No line was added to `oasdiff-accepted-breaking-changes.txt`; adding one would have been
  a permanent suppression bought for a break that does not exist.
- **Replay was left untouched, deliberately** — ADR-0013 says so in as many words, and the issue
  repeats it. The replay view therefore does NOT carry `advisory`. `replayOfAnAdvisoryDecisionIsThe
  SameThreadAsAnyOther` pins that an advisory decision replays as the identical thread shape. This
  conflicts with issue 07's AC ("the MDR replay shows the Entry with … `advisory = true`") — see
  the notes below.
- **Chain verification "before and after the migration" is only satisfiable in one sense.** The
  field joins the hashed payload, as the issue instructs, and the canonical projection emits nulls
  rather than omitting keys (its documented rule, so a future field cannot silently change a
  historical hash). A row hashed *before* the key existed therefore cannot re-derive its stored hash
  now — no `advisory` value can rescue it, since the key itself was absent from the payload. That is
  the deliberate change the issue's own third bullet describes, and it costs nothing here: there is
  no live `mdr_entries` data (dev runtime torn down; `malai_test` is recreated by `malai-db.sh`; the
  demo estate is re-appended through the service layer on every seed). What IS pinned: one chain
  walking advisory rows, non-advisory rows and a disposition together (`rowsVerified` 3), and the
  seeder's `the mdr chain verifies Ok after seeding the demo worklist` still green.
- **Hash coverage is proven by tamper, not by inspection.** `tamperingTheAdvisoryFlagMakes
  VerificationReportTheMismatchNamingThatSeq` flips the stored flag with raw SQL and expects
  `CURRENT_HASH_MISMATCH` at that seq — the same shape as the existing hash-tamper test. A second
  assertion re-derives a stored row's hash with the flag inverted and expects a different digest.
- **The seeder passes `advisory = false` explicitly** at both call sites rather than leaning on the
  default, since every other field there is named including the nulls, and "carried on the
  seeded-data path" reads better as visible than as inferred.

Red baselines proved by mutation rather than assumed (the tests were written first, but Kotlin's
compile coupling makes a fresh-test red weak evidence):
- Neutering the filter to `.filter { advisory == null || true }` → `worklistCarriesAdvisoryAnd
  NarrowsToEitherKind` and `theReviewersDefaultViewIsNonAdvisoryDecisionsAwaitingARuling` both fail.
- Removing `"advisory" to fields.advisory` from the canonical JSON → the tamper test and the
  hash-coverage assertion both fail. (Note this mutation is invisible to the *existing* chain tests:
  writer and verifier share one projection, so dropping a field from both sides still verifies —
  which is exactly why the tamper test was needed.)

- **Three e2e worklist assertions were missed by the staged work and are now fixed.** The summary
  widening is source-compatible but not *assertion*-compatible: `MdrTest` and `PaymentScreeningTest`
  pin the summary with `noExtraProps()`, so every one of them breaks the moment `advisory` joins the
  row. `mvn install` cannot catch this — `malai-end-to-end-tests` sets `e2e.skip=true`, so those
  sources compile but never run outside the stack-backed CI stage. Found by grepping every consumer
  of `dispositionCount` rather than by the build. All three now carry `"advisory" To false`, which is
  correct for both (screening's decisions park payments; the MDR e2e writes without the field and so
  exercises the default). **Corrected by inspection against the summary contract, NOT run** — see the
  note below.

- **A fourth call site was missed and broke the build.** `RecordedModelDecision` gained `advisory`
  with no default — correct for an internal write model, since it forces every writer to declare
  rather than inherit — but `VerifyChainCommandIntegrationTest.aCompletedModelDecision()` in
  `malai-seeder` constructs it with named arguments and was left untouched, so `malai-seeder`'s test
  sources did not compile. Found by grepping every construction site of the three changed data
  classes (`RecordedModelDecision`, `RecordModelDecisionRequest`, `MdrEntryFields`) before running
  anything, then confirmed by the build. Now passes `advisory = false` explicitly, like the two
  `SeedCommand` sites.

- **The oasdiff claim was re-verified, not inherited.** The exact CI invocation from
  `.gitlab-ci.yml` (`tufin/oasdiff:v1.10.6 breaking … --fail-on ERR --err-ignore
  ./malai-api/oasdiff-accepted-breaking-changes.txt`) was run locally against `HEAD`'s contract:
  exit 0. The AC anticipated a break to approve; there is none, and no suppression line was added.

Files: `V27__mdr_entries_advisory.sql` (new); mdr-service `MdrModels`, `MdrReadModels`,
`MdrEntryCanonicalJson`, `MdrEntryRowMapper`, `MdrEntryWriter`, `MdrDecisionService`,
`MdrReadService`, `MdrChainVerifier`, `AdminMdrDecisionsController`; mdr-service tests
`WithMdrEntries` (+`theAdvisoryFlagOfMdrEntryAtSeqIsTamperedTo`,
+`theMdrEntryAtSeqPredatesTheAdvisoryColumn`), happy `RecordModelDecision` /
`AdminMdrDecisions` / `AdminMdrChain` / `RecordHumanDisposition`, sad `AdminMdrDecisions`, writer;
`malai-mdr-client` model + `MdrClientTest`; `RecordModelDecisionRequestBuilder`; payments-service
`ScreeningPaymentsControllerTest` (the wire body it asserts now carries the field);
`SeedCommand` + `SeedCommandIntegrationTest` + `VerifyChainCommandIntegrationTest`; `openapi.yaml`;
e2e `MdrTest` + `PaymentScreeningTest` (the three worklist-summary assertions).

### Landing pass — verified rather than trusted

The implementation above was found complete in the working tree. Everything it claims was re-checked
before committing, and one claim it asserted in prose is now proven:

- **The row-shape CHECK was proven to bite, not read.** The resolution described the constraint's
  intent; nothing exercised it, because the writer can only produce well-shaped rows. Probed
  directly on a throwaway database carrying V25 + V27 alone, across all four shapes:
  `MODEL_DECISION` + `advisory = true` accepted; `HUMAN_DISPOSITION` + `advisory = true` **rejected**
  with `mdr_entries_kind_shape`; `HUMAN_DISPOSITION` + NULL accepted; `MODEL_DECISION` + NULL — the
  pre-migration shape — accepted, which is the "no backfill needed" promise holding at the schema
  level rather than at the reader's `?: false`.
- **V27 applies to a from-scratch database**, not just to one that happens to have the column:
  `malai-db.sh` recreated `malai_test` and applied 26 migrations to v27 cleanly.
- **The oasdiff result was re-run, not inherited a second time.** Exit 0 against `HEAD`'s contract,
  and the changelog names exactly the two info-level changes claimed — `new-optional-request-parameter`
  (the query param) and `response-required-property-added` (the summary field). No suppression line.
- **Every construction site was re-enumerated** for the three changed data classes plus every
  `mdr_entries` query in the repo. All accounted for; `MdrDispositionService`'s two targeted lookups
  and the e2e `WithMdrService` projection select named columns that exclude `advisory`, so neither
  needed touching.
- **Hash coverage needs no mutation to believe.** `tamperingTheAdvisoryFlagMakesVerificationReport
  TheMismatchNamingThatSeq` passing green *is* the proof: a stored-flag flip could only produce
  `CURRENT_HASH_MISMATCH` if the field is genuinely in the hashed payload.
- ADR-0013 and the **Advisory** term in `malai-services/mdr-service/CONTEXT.md` already existed (the
  `copilot: docs.` commit), so no doc gap was left behind.

## Notes for the next iteration

- **Issue 04 is now unblocked on all three prerequisites** (01, 02, 03). It is the tracer bullet and
  the next thing to build.
- **The three e2e assertion fixes are unrun.** They need the stack (`start-e2e-environment.sh`), and
  `e2e.skip=true` keeps them out of `mvn install`. Issue 07 is the first thing that actually runs
  them, so it should treat a summary-shape failure there as this correction being wrong rather than
  as its own regression. The same trap is now known: **any future field on `MdrDecisionSummaryView`
  breaks three `noExtraProps()` assertions that no local build executes.**
- **Issue 07's replay AC needs a decision.** It asserts `advisory = true` on the MDR replay route,
  which this issue deliberately did not add (ADR-0013: "replay … untouched"). Two ways out, both
  cheap: add `advisory` to `MdrModelDecisionView` when 07 lands — an additive, non-breaking response
  field, and arguably the honest reading since the replay view already carries every other
  model-side column — or have 07 assert the flag through the worklist summary, which carries it
  today. The first needs a one-line ADR-0013 amendment; the second needs nothing.
- **Nothing in the repo writes `advisory = true` yet.** Screening's decisions are non-advisory by
  definition (they park payments) and the seeded worklist is all non-advisory, so the flag is
  exercised only by tests until issue 04's copilot decision sets it.
- **The webapp's Ops worklist will start receiving the field** on every summary row. Harmless
  (unknown fields are ignored by the generated client), but `malai-webapp` is where the reviewer's
  default view actually gets narrowed — the backend now offers the parameter, nobody sends it.
