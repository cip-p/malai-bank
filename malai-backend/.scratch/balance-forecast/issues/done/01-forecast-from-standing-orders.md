# 01 — Balance forecast, end to end from standing orders

Status: done

## Parent

`.scratch/balance-forecast/PRD.md`

## What to build

The spine of the balance-forecast feature: a customer opens an account and sees a **Balance forecast** for the end of the current calendar month, driven by that account's active **standing orders** and its available balance. This slice cuts through every layer and is demoable on its own with standing orders alone; direct debits come in slice 02.

`account-service` exposes a new internet-facing `GET /api/v1/accounts/{accountUid}/forecast` (CUSTOMER-only, ownership-verified, no query params — always the current month from `TimeService`). It verifies ownership, reads the account's **available balance** from `ledger-service`, reads the account's active standing orders from a new internal read path on `standing-order-service`, assembles the facts, and calls a new internal endpoint on `insights-service` which computes and returns the forecast. Because the whole screen *is* the forecast, the endpoint **fails closed**: any downstream failure surfaces as an error, never a partial forecast.

The computation lives in `insights-service` as the deep module `BalanceForecastAggregator` — a pure function over `(asOfDate, availableBalanceMinorUnits, currency, instruments)`. It projects each active instrument's **Occurrence**s across the calendar month with the shared `OccurrenceSchedule` (fixed day-of-month, clamped to month-end), splits them at today into **Completed occurrence**s (date ≤ today, display-only) and **Expected occurrence**s (date > today), negates amounts to signed money-out, and returns per-list totals plus `forecastBalance = availableBalance + Σ(Expected)`. insights stays HTTP-client-free — all fan-out is in `account-service`.

OpenAPI is edited **first**: the new public path and `BalanceForecastResponse` (summary fields `accountUid`, `month`, `windowStart`, `windowEnd`, `asOfDate`, `currency`, `availableBalanceMinorUnits`, `completedTotalMinorUnits`, `expectedTotalMinorUnits`, `forecastBalanceMinorUnits`; `completed`/`expected` arrays of occurrences with `date`, `counterpartyName`, `amountMinorUnits` signed negative, `type` = `STANDING_ORDER`, `instrumentId`; no `entryId`). All additive.

## Acceptance criteria

- [ ] OpenAPI edited before controllers; all changes additive, the `oasdiff` backwards-compatibility check stays green
- [ ] `BalanceForecastAggregator` is a pure, I/O-free module with exhaustive unit tests: Completed/Expected split at `asOfDate` (occurrence dated exactly today counts as Completed), per-list totals, `forecastBalance = availableBalance + Σ(Expected)`, signed amounts, date-ascending order with a deterministic tiebreak, month-end clamping in shorter months, weekly-multiple occurrences within one month, non-`ACTIVE` instruments excluded, start-date-mid-month, and the empty-instruments case (`forecastBalance == availableBalance`, empty lists)
- [ ] Internal `POST /api/v1/internal/insights/balance-forecast` (service-token, `@InternalEndpoint`) derives the window from `asOfDate` and returns the forecast body; acceptance test with `WithStubbedTime` and a strict Kacey `containsProp(json { … noExtraProps() })` contract
- [ ] New internal service-token, per-account read endpoint on `standing-order-service` returning that account's **active** standing orders, backed by a `findActiveByAccountId` repository method (custom repository function is tested); a read-scoped service token, not the lodge `onboarding.write` scope
- [ ] New `malai-standing-order-client` client pair (`malai-standing-order-client` + `-test-support`) with a per-account active-orders read method; registered in the root `pom.xml` in **both** `<modules>` and `<dependencyManagement>`; WireMock-backed client test covers request construction (method, URL, service token, `accountId`) and response deserialisation
- [ ] New `balanceForecast(serviceToken, request)` method on `malai-insights-client` with its own WireMock-backed contract test
- [ ] `account-service` `GET /accounts/{accountUid}/forecast`: ownership-verified (not-owned / unknown account → 404 or 403 per the existing account-endpoint convention); reads available balance (ledger − held) and active standing orders, calls insights, returns the passthrough with `accountUid` added
- [ ] Endpoint **fails closed**: if `ledger-service`, `standing-order-service`, or `insights-service` is unavailable the request errors rather than returning a partial forecast (unlike the transactions feed's fail-open)
- [ ] Read-only: no `Idempotency-Key`, no audit events emitted
- [ ] `account-service` acceptance test asserts the composed response **and every outgoing request** (`madeTheHttpRequest` for the ledger-balances call, the SO internal call, and the insights-forecast call carrying the assembled instruments), plus the ownership failure and the fail-closed behaviour, in the existing happy/sad Kensa style
- [ ] New e2e method in `InsightsTest` seeds active standing orders + a starting balance relative to real now, calls the forecast endpoint, and asserts the `AccountService → LedgerService`, `AccountService → StandingOrderService`, and `AccountService → InsightsService` arrows plus clock-robust invariants (`forecastBalance == availableBalance + expectedTotal`; seeded orders appear as `STANDING_ORDER` occurrences with the right amount/counterparty in the correct Completed/Expected bucket) — no brittle golden JSON, no test-only time control
- [ ] `insights-service` remains HTTP-client-free; the ArchUnit rules enforcing this stay green

## Blocked by

None - can start immediately
