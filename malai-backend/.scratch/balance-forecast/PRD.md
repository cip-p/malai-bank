# PRD: Balance forecast — project the month-end balance from recurring instruments

Status: ready-for-agent

## Problem Statement

A malai customer looking at their account today sees a single number — the balance right now — and nothing about what's coming. But their month is already largely committed: rent leaves on the 10th, the energy direct debit collects on the 15th, the gym standing order goes out on the 28th. A real UK retail bank (HSBC's "Balance after bills" / balance forecast is the reference here) tells the customer *where their balance is heading* by the end of the month, splitting the recurring payments into the ones that have already gone out and the ones still to come, and flagging when the account is heading into the red.

Without this, malai shows a ledger's "now" but not a bank's "where this is going". A bank decision-maker watching the demo — whose headline question is *"this looks like a real bank, and the architecture beneath it is one we'd actually want to run"* — sees a gap exactly where a modern retail app is most helpful. And malai already holds the raw material: every account's **Standing order**s and **Direct-debit mandate**s carry a known amount and a known schedule. The forward view is latent and unbuilt.

The demo needs one thing it does not have: **a per-account projection of where the spendable balance lands by month-end**, given the recurring payments still to come — with the payments already made this month shown for context, and a clear warning when the forecast dips below zero.

## Solution

Extend the internal `insights-service` bounded context — already malai's "analytics read-model over the ledger" — to also own a **Balance forecast**: a *deterministic projection* of an account's active **Recurring instrument**s (its **Standing order**s and **Direct-debit mandate**s) onto the current calendar month. insights' charter broadens from "read model over the ledger" to "the analytics and forward-projection layer beside a swappable core". It continues to hold no money state and keeps **no outgoing HTTP clients**.

The forecast is **not** inferred from transaction history. malai already models the recurring payments as first-class instruments with authoritative schedules, so the forecast reads those directly — no repetition detection, no statistical guessing. A **Recurring instrument** is a known mandate, not a detected pattern. (See ADR `insights-service/docs/adr/0002`.)

`account-service` remains the single internet-facing surface and the composer. A new `GET /api/v1/accounts/{accountUid}/forecast` verifies ownership, fans out to `ledger-service` for the account's **available balance**, to `standing-order-service` and `direct-debit-service` for the account's active instruments, hands the assembled facts to `insights-service`, and returns the computed forecast. Because the whole screen *is* the forecast, it **fails closed**: if any downstream is unavailable, the endpoint errors rather than returning a partial view (mirroring `GET /spending`, and unlike the transactions feed which fails open).

insights projects each active instrument's **Occurrence**s across the month using the shared occurrence-schedule math (fixed day-of-month, clamped to month-end in shorter months; weekends ignored), then splits them at **today**:

- A **Completed occurrence** is dated on or before today — already reflected in the available balance, shown for context, and **never subtracted again**.
- An **Expected occurrence** is dated after today through month-end — these are the amounts that move the forecast.

The headline number is `forecastBalance = availableBalance + Σ(Expected)` in signed minor units. The projection may go negative even though a `NO_OVERDRAFT` account's *real* balance cannot — that negative projection is the entire point: it is the "you look fine now, but you won't be by month-end" warning. The forecast is **outgoing-only** — standing orders push money out, direct debits pull money out — so income never appears, consistent with insights' existing **Spending category** opinion.

malai models **no arranged-overdraft facility** (only a binary `NO_OVERDRAFT` guard), so v1 introduces none. The warning state is simply `forecastBalance < 0`, which the webapp derives from the sign; the backend carries no overdraft limit and no redundant "breaches" boolean.

Every freshly-registered demo user gets a forecast worth looking at: the registration seeder places active instruments whose occurrences straddle today (populating both Completed and Expected), sized so the forecast goes negative and trips the warning, with matching backdated ledger postings for the completed occurrences so the available balance stays coherent with the completed rows shown.

The buyer reading the architecture sees: a forward-looking analytics view cleanly separated from the book of record and from the instrument services (so a future Mambu core swap loses no forecast capability, because the schedule truth was never core state); a projection engine that is a pure, isolated, exhaustively-tested function; and a customer-facing screen that honestly refuses to invent an overdraft the bank doesn't offer.

## User Stories

### The forecast number (the headline)

1. As a malai customer, I want to see my forecast balance for the end of the current month on my account, so that I know where my money is heading, not just where it is now.
2. As a malai customer, I want the forecast to account for the recurring payments I still have coming this month, so that the number reflects my real commitments.
3. As a malai customer, I want the forecast to start from my available balance (what I can actually spend now), so that the projection is grounded in my spendable money rather than a figure inflated by pending holds.
4. As a malai customer, I want the forecast to go negative and be flagged when my upcoming payments exceed my available balance, so that I get an early warning before I run out of money.
5. As a malai customer, I want to see the total of the payments still expected this month, so that I understand how much is still due to leave my account.
6. As a malai customer, I want to see the total already spent on recurring payments this month, so that I can see what has gone out so far.
7. As a bank decision-maker watching the demo, I want the forecast headline to name the date it forecasts to (the month-end), so that the projection reads like a real bank's "balance forecast for 31 July".

### Completed vs Expected timeline

8. As a malai customer, I want the recurring payments that have already gone out this month listed as Completed, so that I can see what has already happened.
9. As a malai customer, I want the recurring payments still to come this month listed as Expected, so that I can see what is still due.
10. As a malai customer, I want each forecast row to show the date, the counterparty name, and the amount, so that I recognise each payment.
11. As a malai customer, I want each forecast row tagged as a standing order or a direct debit, so that I understand what kind of recurring payment it is.
12. As a malai customer, I want the expected payments listed in date order, so that I can read them as a timeline of what's coming next.
13. As a malai customer, I want a forecast row to link back to the underlying standing order or direct debit, so that I can inspect or manage the instrument behind it.
14. As a malai customer, I want the forecast to only include my recurring instruments — not every transaction on the account — so that the timeline stays about my committed payments, not my discretionary spending.
15. As a malai customer, I want incoming money like my salary excluded from the forecast, so that the projection is about outgoings, matching how a bank presents it.

### Scope, correctness and edge cases

16. As a malai customer, I want the forecast scoped to the one account I'm viewing, so that it lines up with that account's balance and transactions.
17. As a malai customer, I want a cancelled standing order or direct debit excluded from my forecast, so that payments I've stopped don't inflate the projection.
18. As a malai customer, I want an instrument that collects more than once this month (e.g. a weekly one) to contribute each of its occurrences, so that the forecast reflects the true number of payments.
19. As a malai customer, I want an instrument whose payment day falls on a day that doesn't exist in this month to be handled sensibly (clamped to the month-end), so that the forecast never skips or misplaces a payment.
20. As a malai customer with no active recurring instruments, I want the forecast to show no upcoming payments and a forecast equal to my available balance, so that the screen renders cleanly instead of erroring.
21. As a malai customer, I want the forecast to only ever cover me for accounts I own, so that my financial projections stay private.
22. As a malai customer, I want the forecast screen to tell me clearly when it can't be produced (a dependency is down) rather than showing a misleadingly incomplete projection, so that I never act on a half-built forecast.

### Demo experience

23. As a newly-registered demo user, I want my account to already have recurring instruments so my forecast is populated the moment I explore it, so that the feature never looks empty.
24. As a newly-registered demo user, I want some recurring payments already completed and some still expected this month, so that both sections of the timeline are populated like the reference screenshots.
25. As a newly-registered demo user, I want my seeded forecast to dip into the red, so that the warning state is visible in a self-guided demo.
26. As a bank decision-maker, I want the completed payments to be reflected in my available balance (not just listed), so that the numbers are internally consistent when I scrutinise them.

## Implementation Decisions

**Bounded context & ownership.** The forecast computation lives in `insights-service`, whose charter broadens to include forward projection. insights keeps **no outgoing HTTP clients** (per its ADR-0001); `account-service` does all fan-out and composition. Recorded in `insights-service/docs/adr/0002-balance-forecast-projects-recurring-instruments.md`.

**Source of truth.** The forecast reads the account's **active** standing orders and direct-debit mandates. No history-based recurrence detection. There is no class of malai recurring payment (no card rail) that isn't already a standing order or direct debit, so instruments give full coverage.

**Deep module — `BalanceForecastAggregator`** (insights-service). A pure function taking `(asOfDate, availableBalanceMinorUnits, currency, instruments)` and returning the forecast view. It owns: projecting each instrument's occurrences within the calendar month (delegating date arithmetic to the shared `OccurrenceSchedule`), splitting occurrences into Completed (≤ today) and Expected (> today), negating instrument amounts to signed money-out, computing `completedTotal`, `expectedTotal`, and `forecastBalance = availableBalance + expectedTotal`, ordering occurrences by date ascending (with a deterministic tiebreak), and handling the empty/cancelled cases. Zero I/O. This is the twin of `SpendingBreakdownAggregator`.

**Window.** Always the **current calendar month** (1st → last day) of the month containing `asOfDate`. `asOfDate` is "today" from the shared `TimeService` (real clock in the deployed stack; settable `StubbedTimeService` under the acceptance-tests profile). The endpoint takes **no `month` query param** — a forecast is inherently about "now". An occurrence dated exactly today counts as **Completed**.

**Occurrence projection.** Uses `OccurrenceSchedule` (anchor = instrument `startDate`, plus n × `Frequency`, clamped to month-end). Enumerate occurrences from the instrument's start until they pass month-end; keep those within the window. Weekends are not special-cased (fixed days only). Cancelled instruments (non-`ACTIVE` status) are excluded before projection. The stored single "next occurrence" date on the instrument is *not* sufficient (weekly instruments have several) and is not relied upon; the projection is recomputed from `startDate` + `frequency`.

**Amounts & signs.** Signed minor units throughout the forecast — occurrence amounts and totals are negative (money out), balances are signed and may be negative in the projection. This deliberately diverges from `SpendingBreakdownResponse`'s positive-magnitude convention: the forecast is a signed money-flow view like the transactions feed. Standing-order amounts are always present; direct-debit mandate amounts are nullable in schema but always populated in v1 (variable mandates are rejected at collection), so every forecastable instrument has a known amount.

**Public API contract** (edit `openapi.yaml` first; additive, so oasdiff passes). New `GET /api/v1/accounts/{accountUid}/forecast`, CUSTOMER-only, ownership-verified, no query params. New `BalanceForecastResponse`:
- summary: `accountUid`, `month` (YYYY-MM), `windowStart`, `windowEnd` (the month-end that headlines "forecast for …"), `asOfDate`, `currency`, `availableBalanceMinorUnits`, `completedTotalMinorUnits`, `expectedTotalMinorUnits`, `forecastBalanceMinorUnits`.
- `completed` and `expected`: arrays of occurrence objects, each with `date`, `counterpartyName` (the payee name for a standing order, the creditor name for a direct debit, unified), `amountMinorUnits` (signed negative), `type` (`STANDING_ORDER` | `DIRECT_DEBIT`), and `instrumentId` (for webapp deep-linking). Occurrences carry **no** `entryId`/`transactionId` — a forecast occurrence is a projection, not a posted ledger entry.

**Internal insights contract.** New `POST /api/v1/internal/insights/balance-forecast` (service-token, `@InternalEndpoint`). Request carries `asOfDate`, `availableBalanceMinorUnits`, `currency`, and the list of active instruments (each with `type`, `counterpartyName`, `amountMinorUnits`, `frequency`, `startDate`, `instrumentId`). insights derives the window from `asOfDate`, projects, and returns the forecast body; `account-service` adds `accountUid` and passes it through.

**Read plumbing.** `account-service` gains internal, service-token, per-account read access to the instrument services:
- New internal read endpoints on `standing-order-service` and `direct-debit-service` returning that account's **active** instruments, backed by new `findActiveByAccountId` repository methods (the cross-table `accounts` join pattern already exists in those repositories).
- New client module pair `malai-standing-order-client` (+ `-test-support`), with a `getActiveStandingOrders(serviceToken, accountId)` method.
- New read method on the existing `malai-direct-debit-client` (+ its `-test-support`) for active mandates by account.
- New `balanceForecast(serviceToken, request)` method on the existing `malai-insights-client`.
- Register `malai-standing-order-client` in the root `pom.xml` **both** `<modules>` and `<dependencyManagement>`.
- The new internal read endpoints require a service-token **read** scope that account-service's token carries — reuse the existing internal read-scope pattern used by the ledger/insights internal reads, not the DD lodge endpoint's `onboarding.write` scope.

**Composition & failure mode.** `AccountsService.getForecast` verifies ownership, reads available balance from `ledger-service`, reads active instruments from the SO and DD services, assembles the request, calls insights, and returns the passthrough. **Fails closed**: any downstream failure surfaces as an error, not a partial forecast.

**No overdraft.** No arranged-overdraft facility in v1. No overdraft field, no "breaches" boolean — the warning is `forecastBalance < 0`, derived by the webapp.

**No writes.** The forecast is read-only: **no `Idempotency-Key`**, **no audit events**.

**Seeding.** Extend the registration seeder so every registered user's primary account carries active instruments whose occurrences straddle today (some completed, some expected), sized so `forecastBalance` goes negative, with **matching backdated ledger postings** for the completed occurrences so available balance reflects them. Day-of-month is anchored relative to real now (as the seeder already does with offsets), accepting the month-boundary caveat the existing SO/DD e2e tests already live with.

## Testing Decisions

Good tests here assert **external behaviour** — the forecast a caller receives and the messages exchanged between services — not internal wiring. The exhaustive, golden, deterministic assertions live where **time is stubbable** (the acceptance layer); the real-stack e2e asserts the fan-out arrows and clock-robust invariants, because the deployed clock cannot be pinned and the endpoint has no `month` param.

- **`BalanceForecastAggregator` (unit, exhaustive).** Fixed `asOfDate` + instruments → assert the exact Completed/Expected split, per-list totals, `forecastBalance`, signs, date-ascending order, month-end clamping, weekly-multiple occurrences, cancelled-excluded, start-date-mid-month, and the empty case. Prior art: `SpendingBreakdownAggregatorTest`.
- **insights forecast controller (acceptance).** `POST /internal/insights/balance-forecast` with `WithStubbedTime`, asserting the response payload with Kacey `responseEntity().containsProp(json { … noExtraProps() })`. Prior art: `InsightsSpendingBreakdownControllerTest`.
- **account-service forecast composition (acceptance).** `GET /accounts/{accountUid}/forecast` with `WithStubbedTime` and WireMock stubs for ledger, SO, DD, and insights. Assert the composed response **and every outgoing request** (the ledger-balances call, the SO internal call, the DD internal call, and the insights-forecast call carrying the assembled instruments) via `madeTheHttpRequest`; assert the ownership 403; assert fail-closed when a downstream is unavailable. This is the primary home of the brief's "test all outgoing and incoming messages". Prior art: the spending cases in `AccountsControllerTest`.
- **Client modules (contract).** New `malai-standing-order-client` test, the new DD read-method test, and the new insights forecast-method test — request construction (method, URL, service token, `accountId`) and response deserialisation, WireMock-backed. New `-test-support` stub interfaces (e.g. `WithStandingOrders`) feed the acceptance and e2e layers. Prior art: existing client-module tests and their `*-test-support`.
- **e2e `InsightsTest` (real stack).** A new test method seeds active SO + DD instruments and a starting balance relative to real now, calls `GET /accounts/{accountUid}/forecast`, and asserts the **four fan-out arrows** (`AccountService → StandingOrderService / DirectDebitService / LedgerService / InsightsService`) exactly as the existing tests assert `AccountService → InsightsService`, plus **clock-robust invariants** on the response (`forecastBalance == availableBalance + expectedTotal`; seeded instruments appear as occurrences with the right type/amount/counterparty; each sits in the correct Completed/Expected bucket per its date vs real-today). No brittle golden JSON, no e2e time control, no test-only query param.

## Out of Scope

- **Webapp UI.** The dashboard forecast card and the forecast screen are built separately in `malai-webapp`, off the frozen OpenAPI contract. This PRD is backend + contract only.
- **Arranged overdraft.** No overdraft facility, limit, or "arranged overdraft" line. The warning is `forecastBalance < 0`.
- **Editable / custom forecast cycle.** HSBC's "Edit cycle" (payday-anchored or otherwise custom windows) — the window is always the current calendar month.
- **History-based recurrence detection.** Explicitly rejected; the forecast reads instruments only.
- **Reconciling Completed occurrences against actual postings.** Completed rows are schedule-projected at nominal amounts; v1 does not correlate them to specific ledger entries.
- **Multi-currency forecasts.** Single account currency (GBP in v1), as elsewhere.
- **Forecasting past or arbitrary months, and any `asOfDate`/`month` override.** The forecast is always "the current month, from now".
- **Adding SO/DD instruments to the canonical Sarah Mitchell seed.** The forecast is showcased via the registration fresh-user path so every registered user benefits.

## Further Notes

- Order of build follows the "OpenAPI first" rule: edit `openapi.yaml` (public contract), then the insights computation + internal endpoint, then account-service composition with the new/extended clients, then seeding, with tests at each layer.
- Glossary terms already recorded in `insights-service/CONTEXT.md`: **Balance forecast**, **Recurring instrument**, **Completed occurrence**, **Expected occurrence**; the history-vs-instrument resolution is captured as a flagged ambiguity.
- The `OccurrenceSchedule` object in `malai-commons` (`occurrenceOn`, `nextStrictlyAfter`) is the reusable occurrence-projection primitive already used by both the SO and DD schedulers — reuse it rather than re-deriving date math.
- `insights-service` must stay HTTP-client-free; ArchUnit rules that keep it so should continue to pass. All instrument/balance reads happen in `account-service`.
