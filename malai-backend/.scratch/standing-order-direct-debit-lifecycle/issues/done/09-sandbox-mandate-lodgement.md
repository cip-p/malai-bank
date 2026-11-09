# 09 — Sandbox mandate lodgement + direct-debit e2e journey

Status: ready-for-human

## Parent

`.scratch/standing-order-direct-debit-lifecycle/PRD.md`

## What to build

The demo driver's way to spawn a mandate live. A new Sandbox surface action lodges a direct-debit mandate: pick a fictional creditor from the counterparty directory, one of the active holder's accounts, a fixed amount, frequency, and a first collection date (today or later — today means the collection lands within about a minute). The mandate row is written synchronously bank-side through a new internal direct-debit client, per the module-pair convention; no AUDDIS-style messaging. The webapp Sandbox gains a third tab, Direct Debits, beside Counterparties and Inbound Payments. This slice completes the direct-debit tracer and includes its cross-stack end-to-end journey: lodge a mandate collecting today, and the collection appears in the account feed.

## Acceptance criteria

- [x] OpenAPI edited first; sandbox lodgement path and schemas defined; backwards-compatibility check stays green
- [x] Lodgement creates an ACTIVE mandate with creditor snapshot, fixed amount, frequency, start-date anchor and next collection date — strict response contract, full-entity database assertion, audit event
- [x] First collection date in the past rejected; account ownership enforced against the active holder; creditor resolved from the fictional counterparty directory
- [x] No Idempotency-Key requirement, consistent with the existing sandbox writes
- [x] New internal direct-debit client module pair with the standard WireMock-backed tests and test-support builders; sandbox acceptance tests assert the outgoing client call
- [x] Webapp: Direct Debits tab in the Sandbox with creditor picker fed from the counterparties list; Vitest, Playwright and accessibility coverage in both themes
- [x] Cross-stack end-to-end journey: lodge a mandate collecting today via the sandbox, poll the transaction feed, assert the collection line appears; the mandate is visible on the customer's direct-debits page

## Progress

2026-07-08 — backend sandbox-lodgement slice landed on `main`. A demo driver can now lodge a
creditor-originated mandate: `POST /api/v1/sandbox/direct-debits` (sandbox-service) resolves the
target account against the active holder (ownership → 404, never 403), snapshots the creditor from
the counterparty directory (`nameOnRecord`), and writes the mandate bank-side through a new internal
direct-debit endpoint. The mandate then collects on the issue-08 requester's clock and settles on
the issue-07 inbound rail.

Key decisions:
- **New internal direct-debit client module pair** `malai-direct-debit-client` (+ `-test-support`),
  modelled on `AccountLookupClient` / `WithAccountLookup`. `DirectDebitLodgementClient.lodge(token,
  LodgeMandateRequest)` → `POST /api/v1/internal/direct-debits` (201). Registered in the root pom
  (`<modules>` + `<dependencyManagement>`) and wired into sandbox (`@ComponentScan` +
  `malai.direct-debit.base-url`). Its own request-construction/deserialisation test is green.
- **Wire dates cross as strings.** `AbstractMalaiClient` serialises with a plain `jacksonObjectMapper()`
  (no jsr310), so the client contract (`LodgeMandateRequest`/`LodgedMandateResponse`) keeps `startDate`
  and the timestamps as `String`s; the direct-debit controller (Spring MVC, jsr310 present) parses/renders
  `LocalDate`/`Instant` on its side. Sidesteps the java.time serialisation gap without touching the shared
  client mapper.
- **Endpoint split.** direct-debit-service gains `InternalDirectDebitsController` (service-token
  authed, `@InternalEndpoint` + `hasAuthority('SCOPE_malai-internal/onboarding.write')`, routed by the
  `/api/v1/internal/**` chain — no `secured.paths` change) → `DirectDebitLodgementService` (`@Transactional`).
  The service owns the authoritative start-date-in-past guard (`MANDATE_START_DATE_IN_PAST`, new
  MalaiCode 7970), the ACTIVE row write with `nextCollectionAt = startDate.atStartOfDay(UTC)` (born-due,
  anchor arithmetic reused downstream), and a **SYSTEM-actor** `DIRECT_DEBIT_MANDATE_LODGED` audit row
  (direct debits are creditor-originated — no customer principal crosses the internal boundary; mirrors
  the inbound collection-settlement audit). `toResponse` extracted to a shared `toDirectDebitResponse()`
  mapper used by both DD services.
- **Sandbox controller** resolves the account (`AccountLookupClient`, 404 on not-owned →
  `ACCOUNT_NOT_FOUND`) and the creditor (`MalaiStubClient.getCounterparties`, 404 on unknown →
  `CREDITOR_NOT_FOUND`, new MalaiCode 8978), then calls the lodge client with the directory's
  `nameOnRecord`. `hasRole('CUSTOMER')`, `MalaiUserWithAccountHolderId`, **no Idempotency-Key**
  (no `ProtectedPath` added), consistent with the other sandbox writes. Returns the lodged mandate
  (`DirectDebitResponse`-shaped). `SandboxDirectDebitRequest` uses typed `Frequency`/`LocalDate` so
  malformed values 400 at deserialisation.
- OpenAPI: additive `POST /api/v1/sandbox/direct-debits` + `SandboxDirectDebitRequest` schema
  (response reuses `DirectDebitResponse`); oasdiff stays clean. The internal endpoint is outside the
  `/api/v1/**` customer contract (like the other `/internal/**` endpoints) so it is undocumented by
  design. No migration (V10+V18 carry every column). Cross-bank test fixtures for the new client pair
  added to the `MalaiSortCodePrefixLintTest` exclusion list, mirroring the stub-client pair.

Files:
- malai-direct-debit-client: `DirectDebitLodgementClient`, `model/LodgeMandateModels`
  (`LodgeMandateRequest`/`LodgedMandateResponse`); test `DirectDebitLodgementClientTest`.
- malai-direct-debit-client-test-support: `WithDirectDebitLodgement` (`lodgeMandateReturns`,
  `CONTEXT_PATH=/direct-debit-service`).
- direct-debit-service: `controller/InternalDirectDebitsController`, `service/DirectDebitLodgementService`,
  `model/LodgeMandateRequest` (new); `DirectDebitsService` + `model/DirectDebitsResponse` refactored to
  the shared `toDirectDebitResponse()` mapper. Tests: happy/sad `InternalDirectDebitsControllerTest`;
  `WithDirectDebits` gains an account-only `anAccountExists` seeder.
- sandbox-service: `controller/SandboxDirectDebitsController` (+ `SandboxDirectDebitRequest`); pom
  (+direct-debit client main + test-support test); `SandboxServiceConfiguration` (@ComponentScan);
  application.properties + application-test.properties (+`malai.direct-debit.base-url`);
  `ApplicationRunSupport` + `SandboxAcceptanceTest` thread the direct-debit base-url + `WithDirectDebitLodgement`
  + `MalaiParty` mapping. Test: `SandboxDirectDebitsControllerTest`.
- malai-commons: MalaiCode +7970 +8978; `MalaiSortCodePrefixLintTest` exclusions.
- malai-api: openapi.yaml path + schema; application.properties + test props (+`malai.direct-debit.base-url`).

Verification: full reactor (`mvn install -pl malai-api -am`, all upstream + aggregator) BUILD SUCCESS.
direct-debit-service green (44: +1 happy lodge full-entity+audit+strict-contract, +2 sad
start-date-in-past + missing-service-token 401; `internalEndpointAnnotationOnInternalPathsOnly` arch
rule now non-vacuous). sandbox-service green (16: +1 happy resolving account+creditor asserting the
outgoing lodge call, +3 sad account-404 + creditor-404 + unauthenticated-401). malai-direct-debit-client
green (1). malai-commons green (MalaiCodeTest uniqueness + sort-code lint). malai-api aggregator boots
green with both new controllers + the lodge client wired in.

Remaining (deferred): the webapp Direct Debits sandbox tab (HITL, `malai-webapp` repo) and the literal
cross-stack e2e journey (needs the full running stack + AWS creds, not reproducible here — same
limitation as issues 06/07/08's last AC). The lodgement is already driven end to end under WireMock:
the sandbox test asserts the exact outgoing internal call, and issues 07/08 verify the rail + clock the
mandate lands on. Issue kept open with this note (not moved to `done/`).

## Blocked by

- 02 — cancel a direct debit (schema)
- 07 — inbound BACS direct-debit rail
- 08 — direct-debit collection requester
