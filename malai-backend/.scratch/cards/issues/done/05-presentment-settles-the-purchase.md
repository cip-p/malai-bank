# 05 — Presentment settles the purchase

Status: done

## Parent

`.scratch/cards/PRD.md`

## What to build

The async half of a card purchase: the Scheme later **presents** the captured amount — possibly less than authorised (pay-at-pump), never more — and the purchase becomes a settled transaction with the merchant's name on the statement.

The stub gains a presentment scheduler: approved authorisations present automatically after a configurable delay (visible pending state in demos; e2e sets it to zero), plus a force-now demo endpoint for pacing. The presentment travels stub → `card-gateway` webhook → a new `cards-scheme-event-presented` FIFO queue (catalog + app-side provisioner + paired DLQ, per convention) → a card-service consumer that settles the authorisation's **Hold** for the presented amount via the ledger's settle-with-amount primitive (remainder released atomically) and records the posting with the merchant name on the ledger entry. The authorisation moves `AUTHORISED → SETTLED`. Because settled purchases are plain ledger entries, the existing account feed, statements, and insights categorisation pick them up with no further work.

Domain language: **Presentment** in `card-service` CONTEXT.md; wire contract joins the shared scheme-contracts module.

## Acceptance criteria

- [ ] Presentment wire contract shared by stub and gateway; carries the scheme correlation id, presented amount, and merchant name
- [ ] `cards-scheme-event-presented` FIFO queue + DLQ (standard max-receive) added to the queue catalog and provisioned create-if-absent on startup, same mechanism as existing queues
- [ ] Gateway acceptance: presentment webhook in → message on the queue out, asserted on the queue side with the canonical message shape
- [ ] card-service consumer acceptance: message in → ledger settle-with-amount call out (WireMock asserts hold id and presented amount) → authorisation row `SETTLED`, full-entity asserted
- [ ] Partial capture: presented amount < authorised settles for the presented amount (ledger request asserted); presented amount > authorised is refused — no ledger call, authorisation unchanged, message handled without poisoning the queue
- [ ] Idempotent consumption: redelivery of the same presentment settles nothing twice (idempotent on the hold id)
- [ ] The settled purchase appears in the account transactions feed with the merchant name (asserted through the existing transactions surface), and available balance equals ledger balance afterwards (hold fully consumed/released)
- [ ] Stub scheduler tests: auto-presentment after the configured delay, force-now presents immediately, the test-id is captured and restored on the scheduled callback thread (existing stub discipline for delayed callbacks)
- [ ] Unknown correlation id on a presentment is handled safely (logged outcome, no crash-loop into the DLQ)
- [ ] Kensa acceptance happy/sad suites; strict Kacey with `noExtraProps()`

## Blocked by

- `.scratch/cards/issues/01-ledger-hold-settle-with-amount.md`
- `.scratch/cards/issues/04-first-authorised-purchase.md`

## Completion note (done)

The async half of the card purchase is complete: the stub presents an approved authorisation
(auto after a configurable delay, or via a force-now demo endpoint) → `card-gateway` presentment
webhook → new `cards-scheme-event-presented` FIFO queue → `card-service` consumer settles the
authorisation's Hold for the presented amount via the ledger settle-with-amount primitive
(remainder released atomically) and moves the authorisation `AUTHORISED → SETTLED`.

Key decisions:
- **Two message shapes, one translator seam.** The scheme wire contract `CardSchemePresentmentRequest`
  (schemeReference, `CardSchemeAmount`, merchantName) lives in `malai-scheme-contracts/card`; the
  internal canonical queue message `CardPresentmentNotify` lives in a **new `malai-card-messages`
  module** (mirrors `malai-payments-messages` for a distinct bounded context — card queue messages
  do NOT belong in the payments module). `card-gateway`'s `CardSchemeTranslator.toPresentmentNotify`
  maps between them; card-service never sees a scheme-shaped message and never depends on
  scheme-contracts.
- **New `CardsQueueCatalog`** (`com.malai.sqs.cards`) added alongside `PaymentsQueueCatalog`;
  auto-scanned by `MalaiSqsMessagingConfiguration`'s `@ComponentScan("com.malai.sqs")`, so
  `QueueProvisioner` create-if-absents `cards-scheme-event-presented.fifo` + its `-dlq.fifo`
  (maxReceive 3) in every JVM that imports the SQS config. `card-gateway` (producer) and
  `card-service` (consumer) both gained `malai-sqs` + `malai-card-messages` deps and now import
  `MalaiSqsMessagingConfiguration`; card-service declares `MalaiSourceQueues` so the consumer's
  outbound ledger call is attributed to card-service on the Kensa diagrams.
- **`card-gateway` presentment leg is async** (the normal per-rail pattern), unlike its synchronous
  auth leg (ADR-0005): `POST /cards/callbacks/present` translates → `CardEventProducer` publishes to
  the queue (messageGroupId = messageDeduplicationId = schemeReference, `.withMalaiTracking()`) →
  returns 202. Mirrors `BacsEventProducer`.
- **`CardPresentmentService` (@Transactional)** looks the authorisation up by `schemeReference`
  (new `findBySchemeReference` derived query, exercised by the happy + unknown acceptance tests per
  the issue-04 repo-test precedent). Guards, in order: unknown ref → log + return; already SETTLED →
  log + return (idempotent on the hold id); not AUTHORISED (e.g. a persisted Decline) → log + return;
  **presented > authorised → refused, log + return, no ledger call** (the over-capture guard sits in
  the consumer, ahead of the ledger, per the issue-01 note). Otherwise it settles the hold for the
  presented amount and flips the row to SETTLED. All refusals **return normally (ack)** rather than
  throw, so a poison/unknown message is consumed, never crash-looped into the DLQ (the
  `AcknowledgementMode.ON_SUCCESS` discipline).
- **Settlement posting** debits the card's account (`-presented`) and credits the shared seeded
  `SCHEME_SETTLEMENT_ACCOUNT_ID` (`00000000-…-010002`, the ALLOW_NEGATIVE clearing account from V14 —
  reused, no new migration), both entries carrying `merchantName`/`description` = the merchant name,
  with `settleAmountMinorUnits = presented` so the ledger releases the remainder atomically. Type
  `CARD_PRESENTMENT`. No new SQL migration; the existing V23 `card_authorisations` row is mutated in
  place (status AUTHORISED → SETTLED).
- **Stub presentment scheduler** copies the `StubSwiftScheme` discipline: a single-thread daemon
  `ScheduledExecutorService`, an in-memory `pending` registry of approved originations keyed by
  schemeReference, `TestIdContext` captured on the origination thread and restored/cleared on the
  scheduled thread so the delayed present callback carries `X-TestId`. `present()` uses
  `pending.remove` (atomic CAS) so the auto timer and force-now never double-present. Force-now:
  `POST /scenarios/cards/present-now` → `presentAllPending()` → `{presented: N}`. Delay config
  `malai.stub.card.present-delay-millis` (default 30s; stub acceptance run uses 2000ms so both the
  auto path and the synchronous force-now path are deterministically testable in one boot).
- No OpenAPI change (the gateway callback is scheme-shaped, the queue message is internal — neither
  is in the public contract, like the other gateways), so oasdiff stays green. `card-service`
  CONTEXT.md already documented **Presentment** (prior grill session) — no doc change needed.

Deferred (consistent with the issue-04 precedent):
- "Settled purchase appears in the account transactions feed with the merchant name" and "available
  balance equals ledger balance afterwards" are proven here at the enabling layer — the settle
  posting carries `merchantName` on the customer-account ledger entry, asserted via WireMock. The
  real feed surface is server-side composition (issue 06; settled purchases are plain ledger rows the
  feed already surfaces) and the real balance projection is a cross-stack e2e concern (issue 13
  journey 1). Same posture as issue 04's deferred balance-drop assertion.

Files:
- scheme-contracts (new file): `card/CardSchemePresentment.kt` (`CardSchemePresentmentRequest`).
- malai-card-messages (**new module**): pom + `com/malai/card/messages/CardPresentmentNotify.kt`; root
  `pom.xml` (`<modules>` + `<dependencyManagement>`).
- malai-sqs (new file): `com/malai/sqs/cards/CardsQueues.kt` (`CardsQueues` + `@Component CardsQueueCatalog`).
- card-gateway: `CardEventProducer` (new); `CardSchemeTranslator.toPresentmentNotify`;
  `CardCallbackController.present` (202); config `@Import(MalaiSqsMessagingConfiguration)`; pom
  (+malai-sqs, +malai-card-messages); main+test props (aws region/sqs prefix + localstack endpoint);
  tests — `CardGatewayPresentmentTest` (webhook → queue, canonical shape), translator unit +present,
  `CapturedCardEvents`/`CardEventCaptureListener`/`CardGatewayAcceptanceTestConfiguration` +
  `theProducedPresentmentEvents` + run-support queue prefix.
- card-service: `CardAuthorisationStatus.SETTLED`; `CardAuthorisationRepository.findBySchemeReference`;
  `CardPresentmentService` + `controller/CardPresentmentListener` (new); config
  `@Import(MalaiSqsMessagingConfiguration)` + `MalaiSourceQueues`; pom (+malai-sqs, +malai-card-messages);
  main+test props (sqs); tests — happy `CardPresentmentConsumerTest` (full settle full-entity + settle
  WireMock body, partial capture, idempotent redelivery), sad `CardPresentmentConsumerTest`
  (over-capture refused, unknown ref safe via FIFO barrier, already-declined ignored); support
  `WithCardService.anAuthorisationExists`/`theSchemePresents`(SqsTemplate publish)/`theAuthorisationOnceSettled`,
  `CardAcceptanceTest.cardServiceMadeTheFollowingHttpRequests`, `CardTestConfiguration` anonymous AWS
  creds, run-support queue prefix.
- malai-stub: `StubCardScheme` (registry + scheduler + present + presentAllPending + test-id restore);
  `StubCardGatewayClient.present`; `StubCardSchemeController` `POST /scenarios/cards/present-now`;
  `application.yaml` present-delay; tests — `StubCardSchemeAcceptanceTest` (auto-present + force-now,
  both assert the X-TestId-bearing present callback), `StubCardSchemeTest` constructor, run-support
  present-delay=2000, `WithStubGateways.theCardGatewayWillAcceptPresentments`.
- malai-end-to-end-tests: `LocalstackTest` queue list + `cards-scheme-event-presented(.fifo/-dlq.fifo)`
  (renamed the "ten catalog queues" test).

Verification (serial mvn, Postgres + Localstack + WireMock up; task's npm test/typecheck are N/A in
this Maven/Kotlin repo): full reactor compiles + installs clean (-DskipTests, EXIT 0). Tests green —
card-gateway 11/0/0, card-service 39/0/0, malai-stub 62/0/0, malai-api 21/0/0 (aggregator boots with
the card producer + consumer + card-queue provisioning in one JVM; arch tests green over
/cards/callbacks/present), payments-service 77/0/0 (SQS regression — the shared CardsQueueCatalog is
harmless). e2e LocalstackTest updated (gated off; compiles).

Notes for next iteration:
- Issue 06 (pending/declined feed composition) unblocked: SETTLED authorisations are plain ledger rows
  the feed already surfaces (merchantName is on the entry). account-service composes only AUTHORISED +
  DECLINED via `idx_card_authorisations_account`; do NOT merge SETTLED (would double-row).
- Issue 10 (reversal + refund) unblocked and templated: add `CardReversalNotify`/`CardRefundNotify` to
  `malai-card-messages`, two more base names to `CardsQueues`, two more producers/consumers, and the
  `pending`-registry + force-now stub patterns extend directly. Remember to add the two new
  queues + DLQs to `LocalstackTest`.
- Issue 13 (e2e) will need `card-service` + `card-gateway` boot apps/images/compose entries and the
  stub present-delay set to 0 (the config knob is in place); journey 1 asserts the real
  PENDING → settled feed transition + balance invariants deferred here.
- Minor: the stub `pending` map is a per-JVM singleton, so the force-now acceptance test asserts the
  response *shape* (`presented: something`) + the testId-filtered present callback rather than an exact
  count (a prior test's undrained pending can inflate the count). Fine for a demo stub; a per-test
  reset hook would tighten it if it ever bites.
