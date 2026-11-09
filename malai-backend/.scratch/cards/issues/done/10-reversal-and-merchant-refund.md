# 10 — Reversal and merchant refund

Status: ready-for-agent

## Parent

`.scratch/cards/PRD.md`

## What to build

The two remaining scheme-originated events. A **reversal** cancels an unsettled authorisation: the Scheme reverses it, card-service releases the **Hold** and moves the authorisation `AUTHORISED → REVERSED` — zero ledger entries, the pending row disappears from the feed, available balance restored. A **Refund** follows a *settled* purchase: a merchant-originated credit back onto the card's account, posted as a **new** ledger entry carrying the same merchant name — the original entry is never rewritten.

Both travel the async path: stub origination (demo endpoints — reverse a pending authorisation, refund a settled purchase) → `card-gateway` webhook → new `cards-scheme-event-reversed` / `cards-scheme-event-refunded` FIFO queues → card-service consumers. Wire contracts join the shared scheme-contracts module.

Domain language: **Refund** and the reversal arm of **Card authorisation** in `card-service` CONTEXT.md.

## Acceptance criteria

- [ ] Reversal and refund wire contracts shared by stub and gateway
- [ ] Both queues + DLQs added to the catalog and provisioned on startup per convention
- [ ] Gateway acceptance: each webhook in → correct message on the correct queue out, asserted queue-side
- [ ] Reversal consumer: hold released (ledger call asserted), authorisation `REVERSED` full-entity asserted, **no** posting recorded, pending row gone from the composed feed, available balance restored
- [ ] Reversal of a non-`AUTHORISED` (already settled/reversed) authorisation is refused safely — no ledger call, no crash-loop
- [ ] Refund consumer: validates the referenced purchase is `SETTLED`, records a credit posting with the original merchant name (ledger call asserted), the credit entry appears in the feed with that merchant
- [ ] Refund referencing an unknown or unsettled purchase is refused safely with a logged outcome
- [ ] Idempotent consumption on redelivery for both events (no double release, no double credit)
- [ ] Stub: demo endpoints originate each event against known authorisations; scheduled/delayed callbacks preserve the test-id thread discipline; simulator tests cover both originations
- [ ] Kensa acceptance happy/sad suites; strict Kacey with `noExtraProps()`

## Blocked by

- `.scratch/cards/issues/05-presentment-settles-the-purchase.md`

---

## Completion note (done)

Both scheme-originated events land, mirroring the issue-05 presentment plumbing exactly.

Key decisions:
- TWO WIRE CONTRACTS + TWO INTERNAL MESSAGES + TWO QUEUES, per the presentment template.
  `CardSchemeReversalRequest{schemeReference}` / `CardSchemeRefundRequest{schemeReference,
  refundReference, amount}` in scheme-contracts; `CardReversalNotify{schemeReference}` /
  `CardRefundNotify{schemeReference, refundReference, refundAmountMinorUnits, currency}` in
  malai-card-messages; `cards-scheme-event-reversed` / `cards-scheme-event-refunded` added to
  CardsQueues.ALL so QueueProvisioner create-if-absents both .fifo + -dlq.fifo (maxReceive 3)
  in every JVM importing the SQS config. card-gateway translates scheme->internal so
  card-service never sees a scheme-shaped message. NO OpenAPI change (gateway webhooks +
  internal queue messages are not public contract, like presentment) — oasdiff no-op.
- REVERSAL IS THE STATUS TRANSITION'S OWN IDEMPOTENCY GUARD (exactly like presentment's
  SETTLED guard): findBySchemeReference -> null? ignore; not AUTHORISED? ignore (safe, no
  ledger call, no crash-loop); else releaseHold (ReleaseHoldRequest, ZERO ledger entries) +
  save status=REVERSED. Redelivery finds REVERSED -> no-op -> exactly one release call
  (proven). "Pending row gone from the composed feed" proven at the enabling layer: REVERSED
  is excluded from the feed read's [AUTHORISED, DECLINED] (theInFlightAuthorisationsForAccount
  asserts empty) — NO account-service change needed (issue 06 already reads only those two).
  "Available balance restored" = the asserted release call (ledger restores it); the real
  projection is e2e (issue 13). "No posting recorded" asserted (zero /transactions calls).
- REFUND NEEDED A DOMAIN RECORD for idempotency: new `card_refunds` table (V24), PK
  refund_reference (a settled purchase can be refunded more than once — each event a distinct
  refundReference; FIFO group=schemeReference, dedup=refundReference). Consumer:
  findBySchemeReference(original) -> null? ignore; not SETTLED? ignore (safe); already recorded
  (existsByRefundReference)? no-op; else postTransaction credit (+amount on the referenced
  purchase's Account, -amount on the shared SCHEME_SETTLEMENT_ACCOUNT_ID ...010002, reused, no
  new migration) carrying the ORIGINAL merchant name RESOLVED FROM THE SETTLED AUTHORISATION
  (guarantees "same merchant name" by construction — the refund message doesn't carry it) +
  save CardRefundEntity. Redelivery -> exactly one credit posting + one refund row (proven).
  The credit is a plain settled ledger row with merchantName, so it surfaces in the feed via
  issue 06's plain-ledger-row composition (real feed surface deferred to issue 13 journey 3,
  per the issue-05 enabling-layer precedent). Never rewrites the original entry.
- STUB IS A THIN RELAY (like `present`): POST /scenarios/cards/reverse{schemeReference} and
  /refund{schemeReference,amountMinorUnits,currency} synchronously POST the scheme webhook to
  the gateway (202); reverse also pending.remove(schemeReference) to cancel any scheduled
  auto-presentment. refundReference is stub-generated (UUID) like schemeReference on auth.
  Synchronous on the request thread, so the X-TestId flows naturally (no scheduler thread to
  restore). "Against known authorisations" is enforced downstream — card-service validates
  existence/state; the sandbox (issue 11) resolves real schemeReferences via the card client.

Files:
- scheme-contracts: card/CardSchemeReversal.kt, card/CardSchemeRefund.kt.
- malai-card-messages: CardReversalNotify.kt, CardRefundNotify.kt.
- malai-sqs: CardsQueues += SCHEME_EVENT_REVERSED, SCHEME_EVENT_REFUNDED.
- card-gateway: CardEventProducer.publishReversed/publishRefunded; CardSchemeTranslator
  .toReversalNotify/.toRefundNotify; CardCallbackController POST /reverse + /refund; tests --
  CardGatewayReversalTest (1), CardGatewayRefundTest (1), CardSchemeTranslatorTest (+2),
  CapturedCardEvents + CardEventCaptureListener (+2 @SqsListener) + theProduced{Reversal,Refund}
  Events.
- card-service: CardAuthorisationStatus += REVERSED; CardReversalService + CardReversalListener;
  CardRefundEntity + CardRefundRepository + CardRefundService + CardRefundListener; V24 migration;
  CardServiceConfiguration source-queues += both; WithCardService (theSchemeReverses/theScheme
  Refunds senders, thereAreNoRefunds, theAuthorisationOnceReversed, theRefund{OnceRecorded,In
  TheDatabase,sInTheDatabase}, theInFlightAuthorisationsForAccount); tests -- happy/sad
  CardReversalConsumerTest (2+2), happy/sad CardRefundConsumerTest (2+2); CONTEXT.md (reversal/
  refund relationship + REVERSED invariant).
- malai-stub: StubCardScheme.reverse/refund; StubCardGatewayClient.reverse/refund;
  StubCardSchemeController /reverse + /refund; WithStubGateways.theCardGatewayWillAccept
  {Reversals,Refunds}; StubCardSchemeAcceptanceTest (+2 originations).
- malai-end-to-end-tests: LocalstackTest += reversed/refunded .fifo + -dlq.fifo (gated).

Verification (serial mvn, Postgres + Localstack + WireMock up; npm test/typecheck N/A in this
Maven/Kotlin repo): full reactor installs clean (-DskipTests EXIT 0). Tests green -- card-service
72/0/0 (64 -> 72; +8: reversal happy/sad 2+2, refund happy/sad 2+2), card-gateway 15/0/0
(reversal 1, refund 1, translator +2), malai-stub 64/0/0 (+2 originations), malai-api 21/0/0
(aggregator boots both listeners + queue provisioning in one JVM; MalaiApiArchitectureTest +
OpenApiSpecTest green). e2e LocalstackTest updated (gated off; compiles). oasdiff no-op (no
public contract change).

Notes for next iteration:
- Issue 11 (sandbox) unblocked: the stub's /scenarios/cards/{reverse,refund} demo endpoints are
  the origination surface; sandbox resolves the settled purchase's schemeReference via the card
  client and passes it to the refund trigger (PAN stays server-side). Reversal has no sandbox AC.
- Issue 12 (seeders): ambient reversal/refund can reuse the same thin-relay endpoints against
  registered PANs' settled purchases.
- Issue 13 journey 3 (e2e) owns the real feed proof: refund -> credit ledger row with merchant
  name -> balances reconcile; card-service/card-gateway still need their own e2e containers.
- FIFO group choice: refund groups on the ORIGINAL schemeReference (orders refunds per purchase),
  dedups on refundReference; if a future partial-refund demo needs concurrency across purchases
  this is already correct.
