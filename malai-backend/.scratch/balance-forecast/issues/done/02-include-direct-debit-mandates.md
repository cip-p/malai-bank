# 02 — Include direct-debit mandates in the forecast

Status: done

## Done note (2026-07-09)

Direct debits now flow into the balance forecast alongside standing orders, mirroring the
slice-01 standing-order path.

- **DD internal read**: `DirectDebitsRepository.findActiveByAccountId` (+3 repo tests),
  `InternalDirectDebitsResponse` model, `DirectDebitsService.activeForAccount`, and a `GET`
  on the existing `InternalDirectDebitsController` (service-token, `@InternalEndpoint`). Read
  scope: reused `onboarding.write` — same conscious deviation slice 01 documented, since the
  codebase still has no internal read-scope pattern; a dedicated read scope is a shared
  malai-security change, deferred. Happy (returns active / excludes cancelled) + sad
  (missing service token → 401) controller tests added.
- **Client**: new `DirectDebitReadClient.getActiveDirectDebits` on the existing
  `malai-direct-debit-client` (kept separate from `DirectDebitLodgementClient`), with
  `ActiveDirectDebitModels`, a WireMock contract test, and `-test-support`
  (`WithDirectDebitsApi` + `ActiveDirectDebitsResponseBuilder`). Mandate `amountMinorUnits`
  kept nullable on the wire to match the schema.
- **Composition**: `AccountsService.getForecast` reads active mandates and merges them
  (appended after standing orders) into the single instrument list to insights, tagged
  `DIRECT_DEBIT` with the creditor name as `counterpartyName`. Null-amount mandates are
  dropped (`mapNotNull`) — defensive; v1 mandates always carry an amount. Fail-closed extends
  to DD: any DD-service failure → 503 (new sad test). Happy acceptance test asserts the extra
  outgoing DD request, the merged instruments, and an interleaved (date-ordered) SO+DD
  response with strict `noExtraProps()`.
- **e2e**: `InsightsTest` forecast case extended to also lodge an active DD (via
  sandbox) and assert the `AccountService → DirectDebitService` arrow plus the
  `DIRECT_DEBIT`-tagged completed occurrence (completed order computed from captured
  instrument UUIDs to match the aggregator's `compareBy(date, instrumentId)` tiebreak).
  Gated behind `e2e.skip=true` — compiles/ships but not run against a live stack this
  iteration (same caveat as slice 01; rebuild the stack + `-De2e.skip=false` to exercise).
- **OpenAPI**: no change — `ForecastInstrumentType` already carried `DIRECT_DEBIT` from slice
  01; verified via grep and the passing `OpenApiSpecTest`.
- **Plumbing**: account-service now depends on `malai-direct-debit-client`(+`-test-support`);
  `malai.direct-debit.base-url` added to account-service main+test props and the acceptance
  boot helper, plus registration-seeder test props and malai-seeder main props (the DD client
  beans now reach those contexts transitively via account-service).

Verification (serial `mvn`): DD-service + account-service full suites 194/0/0, malai-api 21/0/0
(incl. OpenApiSpecTest), DirectDebitReadClientTest green, registration-seeder integration 9/0.
`mvn -DskipTests install` compiles the whole affected reactor (DD client, DD service,
account-service, seeders, malai-api, e2e).

Follow-on: issue 03 (seed a populated, warning-tripping forecast) is now unblocked — both
01 and 02 have landed.

## Parent

`.scratch/balance-forecast/PRD.md`

## What to build

Extend the forecast to include the account's active **Direct-debit mandate**s alongside its standing orders, so the timeline shows every kind of recurring payment tagged by type — matching the reference screenshots where rows carry a Standing order or Direct Debit tag.

`account-service` gains an internal read path to `direct-debit-service` and, in the forecast composition, reads the account's active mandates in addition to its standing orders, merging both into the single instrument list handed to `insights-service`. The `BalanceForecastAggregator` needs no change — it is already type-agnostic, projecting and labelling each instrument by the `type` it is given — so this slice is purely the direct-debit read path, the composition merge, and the tests that prove direct-debit occurrences appear. Direct-debit mandate amounts are always populated in v1 (variable mandates are rejected at collection), so every mandate is forecastable.

## Acceptance criteria

- [ ] New internal service-token, per-account read endpoint on `direct-debit-service` returning that account's **active** mandates, backed by a `findActiveByAccountId` repository method (custom repository function is tested); read-scoped service token
- [ ] New per-account active-mandates read method on the existing `malai-direct-debit-client` (+ its `-test-support`), with a WireMock-backed contract test covering request construction and response deserialisation
- [ ] `account-service` forecast composition reads active direct debits and merges them with standing orders into the single instrument list sent to insights; direct-debit occurrences are tagged `DIRECT_DEBIT` and carry the creditor name as `counterpartyName`; cancelled mandates are excluded
- [ ] Fail-closed behaviour extends to `direct-debit-service`: if it is unavailable the forecast errors rather than returning a standing-orders-only partial
- [ ] `account-service` acceptance test asserts the additional outgoing DD internal request (`madeTheHttpRequest`) and a composed response that interleaves standing-order and direct-debit occurrences in date order, in the existing Kensa style with a strict `noExtraProps()` contract
- [ ] `InsightsTest` e2e case (extended or new) seeds active direct debits alongside standing orders and asserts the `AccountService → DirectDebitService` arrow plus `DIRECT_DEBIT`-tagged occurrences appearing with the right amount/counterparty in the correct Completed/Expected bucket
- [ ] OpenAPI `type` enum already covers `DIRECT_DEBIT` from slice 01; no further contract change beyond what 01 shipped (verify and note if any)

## Blocked by

- `.scratch/balance-forecast/issues/01-forecast-from-standing-orders.md`
