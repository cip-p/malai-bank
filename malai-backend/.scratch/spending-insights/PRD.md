# PRD: Spending insights — categorisation, breakdown, and recategorise

Status: ready-for-agent

## Problem Statement

A bank decision-maker exploring the malai webapp sees a flat transaction list: "Tesco — weekly shop", "TfL — daily commute", "Pret a Manger", one after another with no sense of *where the money goes*. Real UK retail banking apps (Monzo, Starling, Revolut) all answer "how much did I spend on groceries this month?" at a glance, with a coloured breakdown and the ability to correct a mis-categorised transaction. Without it, malai looks like a ledger, not a bank — and the buyer's headline question ("this looks like a real bank, and the architecture beneath it is one we'd actually want to run") goes half-answered on the retail-experience side.

The merchant seed data already carries human-readable narratives ("Tesco — weekly shop", "Octopus Energy — DD") on every ledger entry, so the raw material for categorisation is present, but nothing interprets it. The `ledger_entries` table even shipped a `category VARCHAR(50)` column (V5) — but nothing ever writes it, and it flows to the public `TransactionItemResponse.category` as a permanent `null`. The feature is latent and unbuilt.

The demo needs three things it does not have:
- **A category on every transaction**, so the feed reads like a real bank statement.
- **A per-account monthly spending breakdown** (the "where your money went" donut), so a viewer sees analytics, not just a list.
- **Live recategorisation** — tap a transaction, change its category, watch the breakdown move — so a viewer who registers and explores *on their own* has something that responds to them, not a static screenshot.

## Solution

Build a new **internal** `insights-service` bounded context (service-token only, exactly like `ledger-service`) that owns spending analytics as a **read-model over the ledger** — it never holds money state and the ledger stays the uninterpreted book of record. It is a pure **Categoriser** plus an **Override** store keyed by ledger `entryId`, with **no outgoing HTTP clients**.

`account-service` remains the single internet-facing surface for the whole transactions/insights experience. It owns ownership and composes: it reads entries from `ledger-service`, hands their facts to `insights-service` for categorisation and aggregation, and forwards **Override** writes. Three customer-visible capabilities result:

- **Categorised feed** — the existing `GET /api/v1/accounts/{accountUid}/transactions` gains a populated `category` on every entry. The nullable contract field springs to life; no webapp contract break. If `insights-service` is unavailable, the feed **fails open**: entries return with `category: null` and the statement still renders — the core statement never depends on an analytics overlay.
- **Spending breakdown** — a new `GET /api/v1/accounts/{accountUid}/spending?month=YYYY-MM` returns per-**Category** debit totals for the month, presented as positive magnitudes, plus a grand total. Only **Spending category**s appear (`INCOME` and `TRANSFERS` are excluded — moving money to yourself, or receiving it, is not spending). This endpoint *is* the insights screen, so it **fails closed** with a 503 when insights is down.
- **Recategorise** — `PUT /api/v1/accounts/{accountUid}/transactions/{entryId}/category` pins an **Override**; `DELETE` clears it, reverting the entry to rule-derived ("auto"). Naturally-idempotent upserts — **no `Idempotency-Key`** (the heavyweight response-caching idempotency mechanism, which carries an open critique, is for non-idempotent POSTs like initiating a payment, not for a per-entry upsert).

**Category** is a closed ten-value taxonomy — `INCOME, GROCERIES, EATING_OUT, TRANSPORT, BILLS, HOUSING, SUBSCRIPTIONS, TRANSFERS, PAYMENTS, GENERAL` — a Kotlin enum in insights, a string enum on the wire (the same convention as `Currency`). Every entry insights is asked about gets a category; unmatched falls to `GENERAL`, so `category` is never null once insights answers.

Categorisation is deterministic ordered **Rule**s in insights code (not a data table, not ML): **Override** by `entryId` first, then `merchantName` keyword, then `description` keyword, then a transaction-`type`/sign default (a debit rail payment → `PAYMENTS`, a credit → `INCOME`, a book transfer → `TRANSFERS`), then `GENERAL`. To make the type/sign default possible, the ledger entries projection gains the transaction's caller-supplied `type` (additive JOIN). The dead `ledger_entries.category` column is **dropped**; `merchant_name` stays on the ledger as uninterpreted narrative — it is a rule *input*, not a stored category.

The buyer reading the architecture sees: an analytics read-model cleanly separated from the book of record (so a future Mambu core swap loses no categories, because categories were never core state), a user-editable category sitting on an *immutable* ledger entry without ever mutating it (the **Override** is insights state), and a core statement that survives its analytics overlay being down.

## User Stories

### Categorised feed

1. As a malai customer, I want every transaction in my list to show a category (e.g. a "Groceries" pill on "Tesco — weekly shop"), so that my statement reads like a real bank's rather than a raw ledger.
2. As a malai customer, I want a recognised merchant to be categorised automatically, so that I don't have to tag every transaction myself.
3. As a malai customer, I want an incoming salary credit categorised as Income (not lumped with spending), so that money in and money out are clearly distinct.
4. As a malai customer, I want a transaction the bank can't confidently place to show as "General", so that it's obvious which items are uncategorised and worth my attention.
5. As a malai customer, I want my transaction list to still load if the insights feature is temporarily unavailable (just without the category pills), so that I can always see my statement.
6. As a bank decision-maker watching the demo, I want the categories to look UK-appropriate (Groceries, Transport, Bills, Housing, Subscriptions), so that the product feels built for a UK retail market.

### Spending breakdown

7. As a malai customer, I want a monthly spending breakdown for an account showing how much I spent per category, so that I understand where my money goes.
8. As a malai customer, I want the breakdown to show each category's total as a positive amount plus a grand total, so that I can read the donut and the headline "you spent £X this month" figure at a glance.
9. As a malai customer, I want money I moved to my own savings (a transfer) excluded from my spending breakdown, so that the total reflects what I actually spent, not money I shuffled between my own accounts.
10. As a malai customer, I want my income excluded from the spending breakdown, so that the donut is about outgoings only.
11. As a malai customer, I want to pick which month's breakdown I'm viewing, so that I can compare a past month to now.
12. As a malai customer, I want a month with no spending to return an empty breakdown (zero total), so that the screen renders cleanly rather than erroring.
13. As a malai customer, I want the breakdown scoped to the account I'm looking at, so that it lines up with the transaction list on the same screen.

### Recategorise (the live demo moment)

14. As a malai customer, I want to tap a transaction and change its category, so that I can correct a mis-categorisation or classify a "General" item.
15. As a malai customer, I want the spending breakdown to reflect my change immediately (the old category's slice shrinks, the new one grows), so that I see my correction take effect.
16. As a malai customer, I want my category choice to stick across refreshes and always win over the automatic rule, so that the bank respects my correction.
17. As a malai customer, I want to revert a transaction back to automatic categorisation, so that I can undo a change I no longer want.
18. As a malai customer, I want to only be able to recategorise transactions on accounts I own, so that my categorisations are private to me.
19. As a malai customer, I want an invalid category value to be rejected, so that my data stays within the known taxonomy.
20. As a bank decision-maker exploring on my own, I want to reclassify a "General" transaction and watch the breakdown update, so that the product responds to me rather than looking like a static screenshot.

### Demo data

21. As a demo operator, I want the seeded account to include a few transactions that fall through to "General", so that the recategorise feature has an obvious home and the breakdown shows a visible "General" slice to tidy up.
22. As a demo operator, I want the seeded recognised merchants to each land in a sensible category, so that the breakdown looks realistic out of the box.

### Platform / architecture (buyer-facing)

23. As a bank architect evaluating malai, I want categorisation to be a separate read-model that never mutates the ledger, so that I can see the core book of record stays swappable (Mambu) and immutable.
24. As a bank architect, I want the analytics service to be internal and the customer-facing composition to stay in one place, so that the service boundaries are clean and there's no dependency cycle.
25. As a bank architect, I want a user's recategorisation to be an audited action, so that the platform demonstrates the same audit discipline for insights as for payments and account changes.

## Implementation Decisions

### New bounded context: `insights-service` (internal)

- Service-token-only, bundled into `malai-api` like the other services, reached by `account-service` via a new client. No user JWT, no ownership resolution — it trusts its caller, exactly as `ledger-service` does. Rationale and rejected alternatives in `malai-services/insights-service/docs/adr/0001-categorisation-is-an-internal-read-model-not-ledger-state.md`.
- **No outgoing HTTP clients.** Its only state is the **Override** table. This is what makes the "test all outgoing and incoming messages" mandate trivial for it — there are no outgoing messages.
- Two pure deep modules inside it, extracted for isolated testing:
  - **Categoriser** — `(entryId, merchantName, description, transactionType, amountMinorUnits, override?) → Category`. Resolves **Override** first, then the ordered **Rule** list, then `GENERAL`. No I/O.
  - **SpendingBreakdownAggregator** — `(entries with categories) → per-Category totals + grand total`, applying the **Spending category** filter (drop `INCOME`, `TRANSFERS`) and summing only debits as positive magnitudes. No I/O.
- Thin controllers/service over those two modules plus an `OverrideRepository`.

### Internal insights API (service-token)

- `POST /internal/insights/categorisations` — body: a batch of entry facts `[{entryId, merchantName, description, transactionType, amountMinorUnits}]`; returns `[{entryId, category}]`. Applies overrides. Used by the feed enrichment.
- `POST /internal/insights/spending-breakdown` — body: `{month, entries:[...]}`; returns `{categories:[{category, totalMinorUnits, transactionCount}], totalMinorUnits}`. Applies overrides, then the spending filter and aggregation.
- `PUT /internal/insights/overrides/{entryId}` — body `{category}`; upserts an **Override**. `DELETE /internal/insights/overrides/{entryId}` — removes it. Both naturally idempotent.
- Both read endpoints receive entries *in the request* rather than reading the ledger themselves — this keeps insights free of a ledger-client and avoids it doing its own ownership-less ledger read; `account-service` (which already holds the ledger-client and ownership) does the read.

### `Category` taxonomy

- Closed enum: `INCOME, GROCERIES, EATING_OUT, TRANSPORT, BILLS, HOUSING, SUBSCRIPTIONS, TRANSFERS, PAYMENTS, GENERAL`. Kotlin enum in insights; string enum on the wire (`.name`), mirrored in `malai-insights-client` per the `Currency` precedent.
- **Spending category** = the taxonomy minus `INCOME` and `TRANSFERS`. Which categories are "spending" is insights' opinion, owned in one place, never pushed to the webapp.

### Categorisation `Rule`s (in code)

- Ordered, sign/type-aware matchers ending in a `GENERAL` fallback: `Override` → `merchantName` keyword → `description` keyword → transaction-`type`/sign default → `GENERAL`.
- Seeded merchant mapping: Tesco → `GROCERIES`; TfL → `TRANSPORT`; Pret → `EATING_OUT`; Octopus Energy, Council Tax → `BILLS`; Rent → `HOUSING`; Spotify, Gym → `SUBSCRIPTIONS`; savings sweep, savings opening → `TRANSFERS`; credit card payment, rail payments → `PAYMENTS`; ACME Salary and inbound credits → `INCOME`.

### `ledger-service` changes

- Entries projection (`findRecentEntriesForAccount`, `findEntriesForAccountInPeriod`, the projection row interface, `LedgerEntryProjection`, `EntriesResponse`) gains the transaction's `type` via a JOIN to `transactions` — **additive**.
- The dead `ledger_entries.category` column is **dropped**; the projection and entity stop selecting/exposing it. This is invisible to the public contract (category was always `null` there and is now filled by insights). Clean-room latitude on schema applies.
- `merchant_name` and `description` remain untouched on the ledger — they are uninterpreted narrative and the rule inputs.

### `account-service` composition

- Feed: after reading ledger entries and verifying ownership, call `insights-client.categorise(...)` and merge `category` onto each `TransactionItemResponse`. Wrap the call in a try/catch — on any insights error/timeout, log and merge `category: null` (**fail open**).
- Breakdown: new endpoint verifies `accountUid` ownership, reads the month's entries from the ledger, calls `insights-client.spendingBreakdown(...)`, wraps the result with account currency + month metadata. **Fails closed** (503) if insights is unavailable.
- Override: `PUT`/`DELETE` controller verifies `accountUid` ownership (path-scoped, per the repo's `@AuthenticationPrincipal` + `accountHolderId` rule), forwards to `insights-client`, and writes a `CATEGORY_OVERRIDDEN` audit event via `AuditWriter` (account-service holds the `MalaiUser`; insights does not). It does **not** verify `entryId ∈ accountUid` — a forged entryId stores a harmless orphan override that never surfaces, since feed/breakdown only read owned accounts.

### New module pair

- `malai-clients/malai-insights-client` — Apache HC5 wrapper extending `AbstractMalaiClient`, methods `categorise`, `spendingBreakdown`, `putOverride`, `deleteOverride`, data classes matching the wire shape.
- `malai-clients/malai-insights-client-test-support` — `WithInsightsApi` WireMock fixture + response builders (`CategorisationsResponseBuilder`, `SpendingBreakdownResponseBuilder`), following the established `With{Domain}` + `builders` package pattern.
- Both registered in root `pom.xml` `<modules>` **and** `<dependencyManagement>`; `insights-service` wired into `malai-api` (dependency + `@Configuration` import).

### Schema (malai-sql migration)

- Create `category_overrides` (`entry_id` PK, `category`, audit columns `created_at`/`updated_at`/`created_by`). Owned by insights; lives in `public` alongside the rest per the shared-Flyway convention.
- Drop `ledger_entries.category`.

### Public contract (`openapi.yaml`, edited first)

- New `Category` string enum schema.
- `TransactionItemResponse.category` referenced to `Category` (verify `oasdiff` accepts narrowing `string` → enum on a response; if it blocks, keep `string` and document the enum values).
- New `GET /api/v1/accounts/{accountUid}/spending` + `SpendingBreakdownResponse` schema `{month, currency, categories:[{category, totalMinorUnits, transactionCount}], totalMinorUnits}`.
- New `PUT` + `DELETE /api/v1/accounts/{accountUid}/transactions/{entryId}/category` + `CategoryOverrideRequest {category}`.

### Error codes (`MalaiCode`)

- An invalid-category code for the override write (400/422), and a code for the breakdown's 503 when insights is unavailable.

### Demo data (`malai-registration-seeder`, `malai-seeder`)

- Add 2–3 unmatched narratives (e.g. "Cash withdrawal", "J Patel", "Corner Shop") to the spending-narrative rotation so a `GENERAL` slice exists and the recategorise flow has an obvious target.

## Testing Decisions

Good tests here assert **external behaviour** — the HTTP request/response shapes crossing service boundaries, the persisted **Override** rows, the categories and totals a caller observes — not internal wiring. Full-entity database assertions are required (any new column on `category_overrides` must force the test to update). Kensa BDD (`given/when/then/and`) with Kacey `responseEntity().containsProp(json { ... noExtraProps() })` throughout. The brief's mandate — "test all outgoing and incoming messages" — is the acceptance bar.

- **`insights-service` (acceptance BDD)** — the `categorisations` endpoint (override-wins, each rule branch, `GENERAL` fallback), the `spending-breakdown` endpoint (spending filter drops `INCOME`/`TRANSFERS`, positive magnitudes, empty month → zero total, per-category totals + count), and `PUT`/`DELETE` override with **full-entity `category_overrides` assertions**. No outgoing WireMock to assert — it has no downstreams.
- **`account-service` (acceptance BDD)** — outgoing WireMock assertions on every `insights-client` call (feed categorise, breakdown, override forward): method, URL, headers, body. Explicitly test the **fail-open** path (insights returns 5xx/times out → feed still 200 with `category: null`) and the **fail-closed** breakdown path (insights down → 503). Assert the merge onto `TransactionItemResponse` and the `CATEGORY_OVERRIDDEN` audit row. Prior art: the `WithPaymentsApi` outbound-shape assertions in `TransfersControllerTest` and the ledger-composition tests already in `AccountsControllerTest`.
- **`malai-insights-client` (WireMock)** — request construction (method, URL, service-token header, JSON body) and response deserialisation for all four methods, using `something` for dynamic values. Prior art: `LedgerClientTest`, `AccountsOnboardingClientTest`.
- **`ledger-service`** — projection tests proving `transactionType` is exposed on both the recent and in-period reads; repository tests for the changed native queries (the repo rule: any custom repository function is tested).
- **Isolated unit tests on the deep modules** — `Categoriser`: every rule branch, override-wins-over-rule, sign/type defaults, `GENERAL` fallback. `SpendingBreakdownAggregator`: spending-category filter, debit-only positive magnitudes, empty input, multiple entries per category. These are pure and tested without Spring.

## Out of Scope

- **Holder-wide breakdown** across all of a holder's accounts (v1 is per-account; holder-wide is a clean additive follow-up — it requires own-transfer de-duplication and N-account reads).
- **Month-over-month trends / deltas** and any budget/target/limit concept.
- **Two-level categories** (category + subcategory).
- **Free-text or user-defined categories** — the taxonomy is a closed enum.
- **A runtime-editable rules table / admin UI** — rules are code; a table is an additive change if a prospect ever needs it.
- **Net (signed) aggregation / refund handling** — the breakdown sums debits only.
- **Making `insights-service` internet-facing** — it is internal; the webapp only ever talks to `account-service` for this surface.
- **Webapp implementation** — lives in `malai-webapp-v1` (regenerate the TS client, category pills on the feed, the spending donut screen, the tap-to-recategorise sheet). This PRD delivers the backend contract the webapp consumes; the webapp work is tracked under the webapp repo's own PRD.

## Further Notes

- **openapi-first**: edit `openapi.yaml` before implementing controllers; `oasdiff` in CI catches drift. Watch the `TransactionItemResponse.category` `string` → enum narrowing — if `oasdiff` flags it as breaking, keep the field `string` and document the enum values instead.
- **Module registration** is the classic footgun in this repo: the new client pair must be in both `<modules>` and `<dependencyManagement>`, and `insights-service` must be imported into `malai-api`, or the aggregator won't pick up the controllers.
- The recategorise loop is the flagship demo interaction: tap a `GENERAL` seeded item → assign `GROCERIES` → the `GENERAL` slice shrinks and `GROCERIES` grows on the same screen. The seed-data GENERAL narratives exist specifically to make this land.
- Domain language and boundaries are captured in `malai-services/insights-service/CONTEXT.md` and registered in the repo `CONTEXT-MAP.md`; the boundary decision is recorded in insights-service ADR-0001. Use **Category**, **Override**, **Rule**, **Spending breakdown**, **Spending category** consistently.
