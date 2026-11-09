# Spending breakdown

Status: done

## Parent

`.scratch/spending-insights/PRD.md`

## What to build

The per-account monthly spending donut. A customer calling `GET /api/v1/accounts/{accountUid}/spending?month=YYYY-MM` gets back how much they spent per **Category** that month, as positive magnitudes, plus a grand total.

Scope of this slice:
- A pure **SpendingBreakdownAggregator** deep module: `(entries with categories) → per-Category totals + grand total`. Sums **debits only** (`amountMinorUnits < 0`) as positive magnitudes, grouped by category, applying the **Spending category** filter — drop `INCOME` and `TRANSFERS` (money in, and money moved to your own accounts, is not spending). No I/O.
- Internal endpoint `POST /internal/insights/spending-breakdown`: body `{month, entries:[...]}` → `{categories:[{category, totalMinorUnits, transactionCount}], totalMinorUnits}`. Categorises each entry (reusing the slice-1 Categoriser), then aggregates.
- `malai-insights-client` gains a `spendingBreakdown(...)` method (+ test-support builder).
- `account-service` gains `GET /api/v1/accounts/{accountUid}/spending?month=YYYY-MM`: verify `accountUid` ownership, read the month's entries from the ledger (the existing period read used by the statement), call `insights-client.spendingBreakdown(...)`, and wrap the result with account `currency` + `month` metadata. **Fails closed** — if insights is unavailable, return 503 (this endpoint *is* the insights screen; there is nothing to degrade to).
- `openapi.yaml` (edited first): `GET /accounts/{accountUid}/spending` + `SpendingBreakdownResponse` schema `{month, currency, categories:[{category, totalMinorUnits, transactionCount}], totalMinorUnits}`. New `MalaiCode` for the 503-unavailable case.

## Acceptance criteria

- [ ] `GET /api/v1/accounts/{accountUid}/spending?month=YYYY-MM` returns per-category debit totals as positive magnitudes plus a grand total for the requested month, scoped to the account.
- [ ] `INCOME` and `TRANSFERS` never appear in the breakdown; credits are excluded entirely.
- [ ] A month with no spending returns an empty `categories` list and a zero total (no error).
- [ ] Requesting an account the caller does not own returns the standard not-found/forbidden response.
- [ ] When `insights-service` is unavailable, the endpoint returns 503 with the new `MalaiCode` (fail-closed).
- [ ] `insights-service` acceptance (Kensa BDD): the spending-breakdown endpoint — spending filter drops `INCOME`/`TRANSFERS`, positive magnitudes, per-category totals + counts, empty-month zero total — with `noExtraProps()` assertions.
- [ ] `account-service` acceptance (Kensa BDD): outgoing WireMock assertion of the breakdown call, the metadata wrap, ownership rejection, and the fail-closed 503 path.
- [ ] `malai-insights-client` tests (WireMock): request + response shape for `spendingBreakdown`.
- [ ] Isolated unit tests on **SpendingBreakdownAggregator**: filter, debit-only magnitudes, empty input, multiple entries per category.
- [ ] `openapi.yaml` updated first; `oasdiff` passes. `mvn install` green.

## Blocked by

- `.scratch/spending-insights/issues/01-categorised-feed-tracer.md`

## Done

Backend slice landed on main. Every acceptance criterion is reproducible in-repo (no running-stack/AWS AC), all covered by tests.

- **`SpendingBreakdownAggregator`** — pure deep module (`List<CategorisedAmount> -> SpendingBreakdownResponse`): debits only (`amountMinorUnits < 0`) as positive magnitudes, grouped by `Category`, dropping the non-spending set `{INCOME, TRANSFERS}`, ordered largest-total-first (tie-break by `Category` ordinal), grand total. No I/O; 5 isolated unit tests (filter, credits excluded, debit-only magnitudes, empty input, no-debits month).
- **insights-service** `POST /api/v1/internal/insights/spending-breakdown` — body `{month, entries:[EntryFact]}` -> `{categories:[{category, totalMinorUnits, transactionCount}], totalMinorUnits}`. `SpendingBreakdownService` reuses the slice-1 `Categoriser` to categorise each entry, then aggregates. Path-based service-token auth at the aggregator (regex `/api/v\d+/internal/.*`), no `@InternalEndpoint` — mirrors the categorisations endpoint. Kensa acceptance (2): filter drops INCOME/TRANSFERS + credits, per-category totals+counts+order, empty-month zero total, all `noExtraProps()`.
- **malai-insights-client** — `InsightsClient.spendingBreakdown(serviceToken, request)`; `WithInsightsApi.spendingBreakdownReturns/spendingBreakdownReturnsServerError` + `SpendingBreakdownResponseBuilder`. WireMock test asserts request (method/URL/service-token/body incl. `month`) + response deserialisation.
- **account-service** `GET /api/v1/accounts/{accountUid}/spending?month=YYYY-MM` — verifies `accountUid` ownership, reads the month's ledger entries via the existing period read, calls `insightsClient.spendingBreakdown(...)`, and wraps with account `currency` + `month`. **Fails closed** — any insights error is caught, `log.warn`'d, and rethrown as `ServiceUnavailableException(INSIGHTS_UNAVAILABLE)` -> `503` (`message: "3998"`). Acceptance: metadata wrap + outgoing WireMock assertion of the breakdown call (method/URL/Bearer/body), empty-month breakdown, ownership `404`, fail-closed `503`.
- **malai-commons** — new `MalaiCode.INSIGHTS_UNAVAILABLE(3998)` (+ `GENERIC_SERVICE_UNAVAILABLE(3999)` bucket), `ServiceUnavailableException`, and its `@ExceptionHandler` mapping to `HttpStatus.SERVICE_UNAVAILABLE`.
- **openapi.yaml** (edited first) — `GET /accounts/{accountUid}/spending` (`month` query param, `200`/`401`/`404`/`503`), `SpendingBreakdownResponse` + `SpendingCategoryTotal` schemas, new reusable `ServiceUnavailable` response. Backwards-compatible additive change; `OpenApiSpecTest` green.
- **Follow-on fix (latent from slice 01):** slice 01 added `malai-insights-client` (an account-service dependency) whose `InsightsClient` is component-scanned wherever `AccountsServiceConfiguration` is imported, but only threaded `malai.insights.base-url` into account-service + malai-api props. slice 01 verified with `mvn install -pl malai-api -am`, which never builds the seeders, so `malai-registration-seeder` (context boot) was left unresolvable. Added `malai.insights.base-url` to `malai-registration-seeder` test props and `malai-seeder` prod props (safe localhost default), matching the standing-order-service precedent (commit 1ee8bc6).

Verification: full reactor `mvn install` BUILD SUCCESS. Targeted green: `SpendingBreakdownAggregatorTest` 5, `InsightsSpendingBreakdownControllerTest` 2, `InsightsClientTest` 2 (categorise + spendingBreakdown), account-service `AccountsControllerTest` happy 18 / sad 15 (incl. the spending metadata-wrap + outgoing-call happy path, empty-month, ownership 404, and fail-closed 503), `OpenApiSpecTest` 2.

Unblocks 04 (recategorise-with-live-breakdown — the override applies in both the categorisations and spending-breakdown paths). Slice 03 (transaction-type default tier) is independent and remains open.
