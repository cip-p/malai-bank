# Categorised feed (tracer bullet)

Status: done

## Parent

`.scratch/spending-insights/PRD.md`

## What to build

The end-to-end thread that lights up a **Category** on every transaction in the feed, establishing every new integration layer for the feature.

A customer calling `GET /api/v1/accounts/{accountUid}/transactions` gets each entry back with a populated `category` (e.g. a `GROCERIES` on "Tesco — weekly shop", `GENERAL` on anything unrecognised). Under the hood: a new **internal** `insights-service` (service-token only, like `ledger-service`) categorises a batch of entry facts; `account-service` composes it into the existing feed.

Scope of this slice:
- New `insights-service` module, registered in the root `pom.xml` `<modules>` **and** `<dependencyManagement>`, and wired into `malai-api` (dependency + `@Configuration` import) so the aggregator picks up its controllers.
- A pure **Categoriser** deep module: `(entryId, merchantName, description, amountMinorUnits, override?) → Category`, resolving the ordered keyword **Rule** list then a `GENERAL` fallback. No I/O. (The transaction-type/sign default tier is a later slice; this slice categorises on `merchantName`/`description` keywords + sign only.)
  - Full keyword mapping for the seeded narratives: Tesco → `GROCERIES`; TfL → `TRANSPORT`; Pret → `EATING_OUT`; Octopus Energy, Council Tax → `BILLS`; Rent → `HOUSING`; Spotify, Gym → `SUBSCRIPTIONS`; savings sweep, savings opening → `TRANSFERS`; credit card payment → `PAYMENTS`; ACME Salary → `INCOME`; unmatched → `GENERAL`.
- `Category` is a closed Kotlin enum (`INCOME, GROCERIES, EATING_OUT, TRANSPORT, BILLS, HOUSING, SUBSCRIPTIONS, TRANSFERS, PAYMENTS, GENERAL`), a string enum on the wire (`.name`, per the `Currency` precedent).
- Internal endpoint `POST /internal/insights/categorisations`: body `[{entryId, merchantName, description, amountMinorUnits}]` → `[{entryId, category}]`. Entries arrive in the request; insights does not read the ledger itself.
- New module pair `malai-insights-client` + `malai-insights-client-test-support` (Apache HC5 wrapper extending `AbstractMalaiClient`; `WithInsightsApi` WireMock fixture + response builders), both registered in the root `pom.xml`.
- `account-service` feed enrichment: after reading ledger entries and verifying ownership, call `insights-client.categorise(...)` and merge `category` onto each `TransactionItemResponse`. **Fail open** — on any insights error/timeout, log and merge `category: null`; the feed still returns 200.
- `openapi.yaml` (edited first): add the `Category` enum schema and reference it from `TransactionItemResponse.category` (verify `oasdiff` accepts the `string`→enum narrowing on a response; if it blocks, keep the field `string` and document the enum values instead).

## Acceptance criteria

- [ ] `GET /api/v1/accounts/{accountUid}/transactions` returns a non-null `category` on every entry when insights is available; recognised seeded merchants map to the categories listed above; unrecognised entries return `GENERAL`.
- [ ] When `insights-service` is unavailable (5xx or timeout), the feed still returns 200 with `category: null` on every entry (fail-open), and the failure is logged.
- [ ] `POST /internal/insights/categorisations` returns one `{entryId, category}` per input entry, applying the keyword rules.
- [ ] `insights-service` has no outgoing HTTP clients.
- [ ] `insights-service` acceptance (Kensa BDD): the categorisations endpoint over each rule branch and the `GENERAL` fallback, with `noExtraProps()` response assertions.
- [ ] `account-service` acceptance (Kensa BDD): outgoing WireMock assertion of the `insights-client` categorise call (method, URL, service-token header, body) plus the merge onto `TransactionItemResponse`; a dedicated test proving the fail-open path.
- [ ] `malai-insights-client` tests (WireMock): request construction + response deserialisation for `categorise`, using `something` for dynamic values.
- [ ] Isolated unit tests on the pure **Categoriser**: every keyword rule branch and the `GENERAL` fallback (no Spring).
- [ ] `openapi.yaml` updated first; `oasdiff` passes (or the field is kept `string` with the enum documented).
- [ ] Root `pom.xml` registers `insights-service` and both client modules in `<modules>` and `<dependencyManagement>`; `malai-api` imports the insights configuration; `mvn install` green across all modules.

## Blocked by

None - can start immediately.

## Done

Backend tracer landed on main. Every acceptance criterion is reproducible in-repo (no running-stack/AWS AC), all covered by tests:

- New `insights-service` (no outgoing HTTP clients): pure `Categoriser` deep module (`entryId, merchantName, description, amount, override? -> Category`), ordered keyword `RULES` + `GENERAL` fallback; `Category` closed enum, string-on-wire (`.name`, `Currency` precedent). `POST /api/v1/internal/insights/categorisations` (path-based service-token auth at the aggregator, no `@InternalEndpoint` — mirrors `ledger-service`).
- New client pair `malai-insights-client` (+`-test-support`): `InsightsClient.categorise(serviceToken, request)`, `WithInsightsApi` WireMock fixture (`categoriseReturns` / `categoriseReturnsServerError`) + `CategorisationsResponseBuilder`.
- `account-service` feed enrichment: `getTransactions` calls `insightsClient.categorise(...)` and merges `category` onto each `TransactionItemResponse`; **fail-open** — any insights error is caught, logged (`log.warn(..., ex)`), and the feed returns 200 with `category: null`. `getStatement` left on the ledger's own `category` (out of scope for this slice).
- `openapi.yaml` (edited first): added the `Category` enum schema component; kept `TransactionItemResponse.category` as `string` with a description documenting the enum values — the backwards-compatible choice, so `oasdiff` cannot block (no narrowing). `malai-api` `OpenApiSpecTest` green.
- Root `pom.xml` registers all three modules in `<modules>` + `<dependencyManagement>`; `malai-api` imports `InsightsServiceConfiguration` and adds `malai.insights.base-url` across prod/test props + the account-service `ApplicationRunSupport`.
- Docs: `insights-service/CONTEXT.md` + ADR-0001 (categorisation is an internal read-model, not ledger state).

Verification: full reactor `mvn install -pl malai-api -am` BUILD SUCCESS (exit 0). Targeted: `CategoriserTest` 7, `InsightsCategorisationsControllerTest` 2, `InsightsClientTest` 1, `AccountsControllerTest` 29 (incl. the insights-merge happy path asserting the outgoing call — method/URL/`Bearer` token/body — and the dedicated fail-open 503 -> null-category path) all green.

Next slices unblocked: 02 (spending breakdown), 03 (type/sign default tier — will add `transactionType` to the ledger projection and thread it through the facts), 05 (GENERAL seed narratives). The `Categoriser.override` param and the `Category`/`Override` domain in `CONTEXT.md` already anticipate slice 04.
