# 13 — Cross-stack e2e: card containers and the three journeys

Status: ready-for-agent

## Parent

`.scratch/cards/PRD.md`

## What to build

Cards join the cross-stack e2e estate. `card-service` and `card-gateway` each get their own boot app, image, and compose entry following the established per-service container pattern (own Spring Boot app class + Dockerfile + compose block on the next free ports), with the standard in-JVM reverse-proxy stubs, source/target system lookups, and party registrations so every card call lands on the Kensa sequence diagrams. The stub's presentment delay is configured to zero for e2e, per the existing delayed-callback precedent.

Three Kensa-diagrammed journeys, asserting wire traffic and clock-robust invariants (no brittle golden JSON):

1. **Debit lifecycle** — sandbox purchase → synchronous auth through stub → gateway → card-service → `PENDING` row in the feed and available balance down → present now → settled entry with merchant, hold gone → freeze → second purchase visibly `DECLINED` with the frozen reason.
2. **Credit journey** — apply → `CREDIT_CARD` account + card exist → purchase → statement figures satisfy the derived formulas → Pay card book transfer → owed drops by the paid amount.
3. **Refund** — settled purchase refunded via sandbox → credit entry appears carrying the same merchant name; balances reconcile.

## Acceptance criteria

- [ ] card-service and card-gateway run as their own containers in the e2e compose stack; the full stack comes up healthy with images built
- [ ] Proxy-path constants, proxy stub priming, from/to system lookups, and parties registered for both new services so inter-service arrows render on the diagrams
- [ ] Queues used by the card flows are app-provisioned against the shared Localstack like existing queues (no init scripts)
- [ ] Journey 1 asserts the arrow chain stub → card-gateway → card-service → ledger for the sync auth, the queue-borne presentment path, the feed's `PENDING → settled` transition, available-balance invariants at each step, and the visible `DECLINED` row after freeze
- [ ] Journey 2 asserts the application's fan-out (account + ledger registration with floor + card issuance), an approved purchase within the limit, `statementBalance/minimumPayment/dueDate` consistent with the derived formulas against seeded amounts, and owed-drops-by-paid-amount after the book transfer
- [ ] Journey 3 asserts the refund event path (stub → gateway → queue → card-service → ledger) and the merchant-named credit entry
- [ ] Assertions are clock-robust (relative to real now, stub presentment delay zero) with no test-only time control, per the existing e2e style
- [ ] e2e remains gated off by default and green when run with the documented flag

## Blocked by

- `.scratch/cards/issues/06-pending-declined-feed-composition.md`
- `.scratch/cards/issues/07-freeze-unfreeze-replace.md`
- `.scratch/cards/issues/09-credit-statement-and-repayment.md`
- `.scratch/cards/issues/11-sandbox-card-triggers.md`

## Completion

Done. Cards join the cross-stack e2e estate; the full stack comes up healthy and the
three Kensa-diagrammed journeys are green.

Key decision — the issue's "own boot app, image, compose entry (per-service container)"
premise was **superseded by the phase-2c topology consolidation** that has since landed:
every malai service (auth … fps/swift/bacs-gateway) is now bundled into the single
`malai-api` JVM, and inter-service arrows render from Kensa envelopes published by
`AbstractMalaiClient` (no WireMock reverse-proxy, no per-service container). `card-service`
and `card-gateway` were already declared in `malai-api` (pom + `MalaiApiApplication` @Import)
and their three FIFO queues app-provisioned by `QueueProvisioner`, so the work was **wiring
them as Kensa parties + the missing stub→gateway base-url + the three journeys** — not new
containers. This matches how the FPS/SWIFT/BACS rails already run in e2e.

Found + fixed a real gap: the stub's `StubCardGatewayClient` reads
`malai.card-gateway.base-url` (default `http://localhost:8080` = the stub's own container),
but the compose only set the present-delay var — so every card journey would have failed at
the synchronous auth. Added `MAL_CARD_GATEWAY_BASE_URL: http://malai-api:8080` alongside the
other gateway URLs on the `malai-stub` service.

- **Journey 1 (debit lifecycle)** — sandbox purchase → arrow chain asserted
  `SandboxService→MalaiStub` (`/scenarios/cards/purchase`), `MalaiStub→CardGateway`
  (`/cards/callbacks/authorise`), `CardGateway→CardService`
  (`/api/v1/internal/cards/authorisations`), `CardService→LedgerService` (hold) → `AUTHORISED`
  in `card_authorisations`, available balance down (held 4200, ledger unchanged), `PENDING`
  feed row with merchant + cardId → present-now (`/scenarios/cards/present-now` →
  `/cards/callbacks/present`) → `SETTLED`, ledger moves, hold gone, plain settled row with
  merchant, `SCHEME_SETTLEMENT` +4200 → freeze → second purchase `DECLINED`, visible feed row
  with `declineReason=CARD_FROZEN`, balance untouched.
- **Journey 2 (credit)** — apply → `CREDIT_CARD` account + `CREDIT` card in the response,
  `AccountService→CardService` (`/api/v1/internal/cards`) issuance arrow; fresh summary
  (owed 0, limit 250000); purchase 30000 → present/settle → derived figures satisfied
  (statementBalance 30000, minimumPayment 750 — the `max(£5, 2.5%)` branch, dueDate = 21st of
  next month computed from real now); Pay-card book transfer 10000 → owed drops to 20000
  (minimumPayment 500).
- **Journey 3 (refund)** — settled purchase → `theSettledAuthorisationIdForSchemeReference`
  resolves the canonical id → sandbox refund (`/scenarios/cards/refund` →
  `/cards/callbacks/refund`) → `card_refunds` row → merchant-named **credit** row in the feed
  carrying the original merchant name; card-account and `SCHEME_SETTLEMENT` ledgers reconcile.

Clock-robust: no test-only time control; stub auto-presentment disabled
(`MAL_STUB_CARD_PRESENT_DELAY_MILLIS=600000`) so `present now` is the deterministic settler;
dueDate derived from real `YearMonth.now`; async transitions asserted by polling Postgres
(`WithCardsDb`); refund/settle ledger rows asserted order-independently (they share a
near-identical `postedAt`). Feed rows asserted by parsing the response (server-generated
authorisation ids + running balances make a golden-JSON match brittle), matching the
`theLedgerEntriesForAccountInTheDatabase` kotest-on-parsed-data idiom.

Files: new `tests/CardsTest.kt` (3 journeys) + `WithCardService.kt` + `WithCardsDb.kt`;
`MalaiE2ETest`/`MalaiE2ETestContext`/`KensaEventConsumer` card party + route wiring;
`WithAccountService`/`WithSandboxService` card request helpers; `deleteAllFixtures` truncates
`cards`/`card_authorisations`/`card_refunds`; `docker-compose.e2e.yml`
(`MAL_CARD_GATEWAY_BASE_URL` + present-delay). `MalaiParty.CardService`/`CardGateway` and the
`LocalstackTest` card-queue assertions already existed.

Verification: e2e gated off by default (`e2e.skip=true`). Brought the stack up
(`start-e2e-environment.sh`) and ran the full suite with `-De2e.skip=false` — **38/0/0 green**
(`CardsTest` 3/3, `LocalstackTest` 1/1 proving the three `cards-scheme-event-*` FIFO pairs are
app-provisioned, every pre-existing test unaffected). e2e module `test-compile` EXIT 0.

Notes for next iteration:
- Issue 12 (seeders + ambient spend) is the last open cards issue; e2e now proves the
  sandbox-driven flows the ambient scheduler mirrors.
- The three journeys presume test isolation of the stub's in-memory `pending` map (each
  journey presents what it authorises); the `presented`-count assertion was deliberately
  dropped for that reason (settlement is proven by the `SETTLED` poll + ledger).
