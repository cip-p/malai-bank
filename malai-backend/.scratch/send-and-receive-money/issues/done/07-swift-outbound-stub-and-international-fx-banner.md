# SWIFT outbound stub + international FX banner

Status: ready-for-agent

## Parent

`.scratch/send-and-receive-money/PRD.md`

## What to build

International outbound payments via a SWIFT-shaped stub rail. A Malai customer can call `POST /api/v1/payments` with `toIban` + `toBic` + `paymentPurpose` (instead of `toSortCode` + `toAccountNumber`); the payment is routed to the SWIFT rail, debits the customer's GBP account, credits `SWIFT_OUTBOUND_SUSPENSE`, publishes `swift.outbound.submit`, and settles ~30 simulated seconds later when `malai-stub` publishes `swift.outbound.settled`. **No return path** in v1 — the SWIFT stub always settles.

The customer-facing FX behaviour: when the request specifies a destination currency different from `GBP` (via the `currency` field in the request, or inferred from the IBAN country prefix), the response includes a `fxConversion` block — a fixed-rate display of the target amount (e.g. `{ amount: 11500, currency: "EUR", rateUsed: 1.15 }`). The customer's GBP account is debited the `amountMinorUnits` value as stated in the request; the target amount is presentational only. No spread, no fees, no fluctuating rate — `StubFxRates` is a static `Map<Currency, BigDecimal>` resource loaded at start-up.

The `payments` table's international-only columns (`debtor_iban`, `debtor_bic`, `beneficiary_iban`, `beneficiary_bic`, `payment_purpose`) — already present from #01 — are populated for SWIFT payments; `beneficiary_sort_code` / `beneficiary_account_no` stay `NULL` for the SWIFT direction. (Mixing both is rejected with `BAD_REQUEST`.)

The slice adds:

- New messages in `malai-payments-messages`: `SwiftOutboundSubmit`, `SwiftOutboundSettled`. Same end-to-end-id correlation pattern as FPS.
- New queues in Localstack: `swift.outbound.submit`, `swift.outbound.settled`.
- New `SWIFT_OUTBOUND_SUSPENSE` system-owned account seeded by a Flyway migration.
- New `StubSwiftScheme` consumer inside `malai-stub`: consumes `swift.outbound.submit`, waits ~30 simulated seconds (configurable; in tests this is 0), publishes `swift.outbound.settled`. Always settles in v1 — there's no return path.
- `RailRouter` (the shallow helper introduced in #02 for FPS-vs-Book) gains a SWIFT branch: when the request carries IBAN/BIC, route to SWIFT.
- `PaymentLifecycleService.transitionToSettled` is unchanged in shape — it works identically for FPS and SWIFT because the lifecycle is rail-agnostic.
- `FxConversionService` — small deep-ish module that, given (sourceCurrency=GBP, targetCurrency, amountMinor), returns the converted target-currency amount and the rate used. Pure function, easily tested. The lookup table is loaded from a single resource file.

The `swift.outbound.submit` payload carries IBAN, BIC, payment purpose, GBP amount, and the FX-converted target amount + currency for stub-side visibility. The stub records what it received (no further action besides settling).

The slice **does not** touch BACS-DC (#08), inbound international (out of scope for v1), or the ambient-activity scheduler.

Domain glossary: `Payment`, `Rail` (= `SWIFT`), `Suspense account`, `Settlement`, `Lifecycle`. The terms `IBAN` and `BIC` are new conceptual carriers — adding them to the glossary is part of this slice's documentation work.

## Acceptance criteria

- [ ] New Flyway migration seeds `SWIFT_OUTBOUND_SUSPENSE`. Filtered from `GET /api/v1/accounts`.
- [ ] `malai-payments-messages` gains `SwiftOutboundSubmit`, `SwiftOutboundSettled`. Localstack provisions `swift.outbound.submit` and `swift.outbound.settled`.
- [ ] `PaymentRequest` schema accepts either UK identifiers (`toSortCode`/`toAccountNumber`) or international identifiers (`toIban`/`toBic`/`paymentPurpose`), not both. Mixing returns `BAD_REQUEST` with a malai code.
- [ ] `RailRouter` infers the rail: UK identifiers → FPS or Book; international identifiers → SWIFT.
- [ ] `Payment` row populated with international columns for SWIFT payments; UK identifier columns stay `NULL`.
- [ ] `FxConversionService` returns target-currency amount and the static rate used; rates resource holds at minimum GBP→EUR and GBP→USD.
- [ ] `PaymentResponse` includes `fxConversion` block when the destination currency differs from source.
- [ ] `StubSwiftScheme` consumes `swift.outbound.submit`, publishes `swift.outbound.settled` after a configurable simulated delay (default 30s; 0s in test profile). `payments-service`'s consumer transitions the payment to `SETTLED`.
- [ ] Acceptance tests cover: SWIFT happy path end-to-end (POST with IBAN → PENDING → SETTLED after the simulated delay), FX conversion block present and correct in response, mixed-identifier rejection, idempotency of the settled-consumer.
- [ ] `malai-stub` tests cover `StubSwiftScheme` always-settle behaviour with the simulated delay.
- [ ] `payments-service/CONTEXT.md` updated with brief definitions for `IBAN`, `BIC`, `Payment purpose`. The existing FX stance (single-currency accounts, fixed rate at send time) referenced.
- [ ] `openapi.yaml` updated: `PaymentRequest` accepts the international identifier variant; `PaymentResponse` exposes `fxConversion`.
- [ ] `mvn install` passes.

## Blocked by

`.scratch/send-and-receive-money/issues/02-fps-outbound-happy-path-malai-to-malai.md`
