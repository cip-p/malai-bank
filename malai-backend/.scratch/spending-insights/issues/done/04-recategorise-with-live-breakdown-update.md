# Recategorise with live breakdown update

Status: done

## Parent

`.scratch/spending-insights/PRD.md`

## What to build

The flagship demo interaction: a customer taps a transaction, changes its **Category**, and both the feed pill and the spending donut reflect it. A user's choice is an **Override** stored by ledger `entryId` that always wins over rule-derived categorisation; it can be cleared to revert to automatic.

Scope of this slice:
- New `category_overrides` table (malai-sql migration): `entry_id` PK, `category`, audit columns (`created_at`/`updated_at`/`created_by`). Owned by insights, lives in `public` per the shared-Flyway convention.
- `insights-service`: `PUT /internal/insights/overrides/{entryId}` (body `{category}`, upsert) and `DELETE /internal/insights/overrides/{entryId}` (remove). Both naturally idempotent — **no `Idempotency-Key`**.
- The **Override** is applied in **both** categorisation paths: the `categorisations` endpoint (feed pill) and the `spending-breakdown` endpoint (donut) resolve override-first, then rules, then `GENERAL`.
- `malai-insights-client` gains `putOverride` / `deleteOverride` methods (+ test-support).
- `account-service`: `PUT` and `DELETE /api/v1/accounts/{accountUid}/transactions/{entryId}/category`. Verify `accountUid` ownership (path-scoped, per the `@AuthenticationPrincipal` + `accountHolderId` rule), forward to `insights-client`, and write a `CATEGORY_OVERRIDDEN` audit event via `AuditWriter` (account-service holds the `MalaiUser`; insights does not). Reject an invalid category value with the new `MalaiCode`. Does **not** verify `entryId ∈ accountUid` — a forged entryId stores a harmless orphan override that never surfaces, since feed/breakdown only read owned accounts.
- `openapi.yaml` (edited first): `CategoryOverrideRequest {category}` + the `PUT`/`DELETE` endpoints. New `MalaiCode` for invalid category.

## Acceptance criteria

- [ ] `PUT /api/v1/accounts/{accountUid}/transactions/{entryId}/category` pins the category; the next feed read shows it and it wins over the automatic rule.
- [ ] After a `PUT`, the spending breakdown for that month reflects the change (old category's total drops, new category's total rises) on the next read.
- [ ] `DELETE` removes the override; the entry reverts to rule-derived categorisation.
- [ ] Recategorising an account the caller does not own is rejected (ownership check on `accountUid`).
- [ ] An invalid/unknown category value is rejected with the new `MalaiCode`.
- [ ] A `CATEGORY_OVERRIDDEN` audit event is written on the override write.
- [ ] Neither write requires an `Idempotency-Key`; replaying a `PUT` with the same body yields the same state.
- [ ] `insights-service` acceptance (Kensa BDD): `PUT`/`DELETE` with **full-entity `category_overrides` assertions**; override-wins verified through both the categorisations and spending-breakdown endpoints.
- [ ] `account-service` acceptance (Kensa BDD): outgoing WireMock assertion of the override `PUT`/`DELETE` forward, the ownership rejection, the invalid-category rejection, and the `CATEGORY_OVERRIDDEN` audit row.
- [ ] `malai-insights-client` tests (WireMock): request shapes for `putOverride`/`deleteOverride`.
- [ ] Isolated unit test on the **Categoriser**: override-wins-over-rule.
- [ ] `openapi.yaml` updated first; `oasdiff` passes. `mvn install` green.

## Blocked by

- `.scratch/spending-insights/issues/01-categorised-feed-tracer.md`
- `.scratch/spending-insights/issues/02-spending-breakdown.md`

## Done note (2026-07-08)

Landed on `main`. insights-service gained its first database: `category_overrides` (V20,
already migrated) is an insights-owned Override store — `spring-boot-starter-data-jpa` +
`postgresql` added to its pom, `@EnableJpaRepositories`/`@EntityScan` on
`InsightsServiceConfiguration`, datasource props in main + test properties (bundled
malai-api still owns the runtime datasource; per-service props only bind on standalone
boot). `CategoryOverrideEntity` (`@Enumerated(STRING)` Category), `CategoryOverrideRepository`,
`CategoryOverrideService` (upsert preserving `created_at`, stamping `updated_at` from the
injected `TimeService`; idempotent `existsById`-guarded delete). `InsightsOverridesController`
PUT/DELETE `/api/v1/internal/insights/overrides/{entryId}` (path-based service-token auth at
the aggregator, no `@InternalEndpoint` — mirrors the other insights internal endpoints; no
`Idempotency-Key`). Both read paths now resolve override-first: `InsightsCategorisationService`
+ `SpendingBreakdownService` look up `overridesFor(entryIds)` and pass `override=` to the
Categoriser (whose `override` param + precedence already existed from slice 01).

account-service: `AccountsController` PUT/DELETE
`/api/v1/accounts/{accountUid}/transactions/{entryId}/category` (`hasRole('CUSTOMER')`).
`AccountsService.overrideCategory` verifies `accountUid` ownership → parses the category
(invalid → `BadRequestException(INVALID_CATEGORY)` = new MalaiCode 7969) → forwards to
`insightsClient.putOverride` → writes a `CATEGORY_OVERRIDDEN` audit event (account-service
holds the `MalaiUser`; insights does not). `clearCategoryOverride` mirrors it with
`deleteOverride` + a `CATEGORY_OVERRIDE_CLEARED` audit row. Does NOT verify `entryId ∈
accountUid` — a forged entryId stores a harmless orphan override, per the issue.

malai-insights-client: `putOverride`/`deleteOverride` (pre-existing production code) now have
WireMock stubs (`putOverrideReturns`/`deleteOverrideReturns` in `WithInsightsApi`) +
`InsightsClientTest` request-shape assertions. `category_overrides` added to the shared
`deleteAllFixtures` truncate list.

Verification: full reactor `mvn install` BUILD SUCCESS. Targeted green: InsightsOverrides
ControllerTest 7 (PUT/DELETE full-entity DB assertions + replay-idempotency + override-wins
through categorisations AND spending-breakdown), insights-service module 29, InsightsClientTest
4 (incl. putOverride/deleteOverride), account-service AccountsControllerTest happy 20 / sad 18
(outgoing PUT/DELETE forward, CATEGORY_OVERRIDDEN audit, ownership 404, invalid-category 400),
OpenApiSpecTest 2. Categoriser override-wins covered by the pre-existing
`overrideAlwaysWinsOverTheRules`. openapi.yaml change is purely additive (new path + schema),
so oasdiff cannot block.
