# PRD: Sandbox service — expose the stub's fictional Counterparty catalogue and trigger simulated inbound payments

Status: ready-for-agent

## Problem Statement

A bank decision-maker watching a malai demo — or an unguided prospect exploring on their own at `app.fcd8ayxqr72.com` — has no way to *see* the controllable surface of the demo platform from inside the product. The webapp can currently take them through a real **Transfer** flow and a real **Payee**-save flow with **Confirmation of Payee** (CoP), and the underlying `malai-stub` already simulates rail outcomes (`SETTLES` / returns) and fictional **Counterparties** the bank's directory recognises. But the only way to *learn* which fictional party will pass CoP, which will fail it, which destination will produce a returned payment, or how to trigger an incoming payment, is to read the YAML config behind the stub. The unguided prospect has no read access to that file; the demonstrator has no in-product narration affordance.

That gap matters for malai's core pitch. The buyer asks "this looks like a real bank, and the architecture beneath it is one we'd actually want to run." Real banking-platform vendors (Mambu, Thought Machine, 10x) expose exactly this surface — a sandbox tier where a bank's QA team or a fintech builder can read the controllable fixtures and trigger inbound test events. malai having a credible analogue of that surface *strengthens* the malai-as-platform narrative, rather than weakening the bank illusion.

The brief asks for:
- a way to see which fictional accounts pass CoP for payee creation,
- a way to see which fictional accounts produce specific outbound payment outcomes (rejected, in-progress, etc.),
- a way to simulate receiving a payment.

The backend has nothing to serve any of this today. The stub's directory is in-process YAML; the stub's inbound endpoints (`POST /demo/inbound/{rail}`) exist but are not reachable from a signed-in webapp user; there is no `/api/v1/sandbox/*` namespace on `malai-api`; there is no `malai-stub-client` (today's architecture has no inbound-to-stub HTTP direction); the stub's `StubFictionalDirectory` rows carry a `returnReason?` field but no `outboundOutcome` enum, and the FPS / BACS scheme handlers don't consult per-row outcome data — they use the one-shot global `armReturn` state instead. The webapp cannot ship the Sandbox surface (see the companion webapp PRD) until the backend provides the read and write endpoints to back it.

## Solution

Introduce a new **`sandbox-service`** bounded context behind `/api/v1/sandbox/*`, exposing two endpoints to the webapp's **Sandbox** surface, and widen `malai-stub`'s `StubFictionalDirectory` to carry the canonical outcome enum that drives every outbound scheme handler.

- **`GET /api/v1/sandbox/counterparties`** — auth-required, no `MalAccountHolderIdHeader` (the catalogue is reference data, not holder-owned). Returns the stub's fictional **Counterparty** rows in the wire shape `{ sortCode, accountNumber, bank, nameOnRecord, outboundOutcome, returnReason? }`. The controller is a thin pass-through to a new `MalaiStubClient.getCounterparties()` call against the stub's new `GET /demo/counterparties` route.
- **`POST /api/v1/sandbox/inbound-payments`** — auth-required, `MalAccountHolderIdHeader` required, `Idempotency-Key` required at the malai-api boundary. Request body: `{ rail: "FPS" | "BACS", creditedAccountId, originatorSortCode, originatorAccountNumber, originatorName, amountMinorUnits, reference? }`. The controller validates that `creditedAccountId` belongs to the active **AccountHolder**, then forwards the payload to the stub's existing `POST /demo/inbound/{rail}` route via `MalaiStubClient.postInbound(rail, payload)`. Response is `202 Accepted` — the resulting **Transaction** appears via the existing accounts / transactions polling. The dedup happens at the malai-api idempotency layer; the stub call is not gated by `Idempotency-Key`.

A new `malai-clients/malai-stub-client` + `malai-clients/malai-stub-client-test-support` module pair establishes the first inbound-to-stub HTTP direction in the architecture, configured from a new `malai.stub.base-url` property. `sandbox-service` is the only service that depends on it; `malai-api` aggregates `sandbox-service` exactly the way it aggregates every other service. The architectural framing is captured in backend ADR-0003 (`malai-stub` gains demo-state-introspection role).

The stub gains:
- a new route `GET /demo/counterparties` returning its `StubFictionalDirectory` rows in the wire shape above;
- a widened `StubAccount` carrying `outboundOutcome: OutboundOutcome` (enum `SETTLES | RETURNS | STUCK_PENDING`), with boot-time validation that `returnReason` is non-null iff `outboundOutcome = RETURNS`;
- a new pure deep-module `OutboundOutcomeResolver` — `(sortCode, accountNumber) → OutboundOutcome` (defaults to `SETTLES` for unknown destinations) — consulted identically by the FPS and BACS scheme handlers per outbound submission. The legacy `POST /demo/arm-return` and `GET /demo/fps/returns` routes stay in place for backwards compatibility with existing tests / scripts but are not on the Sandbox path.

YAML fixtures grow to: one row per `ReturnReason` value (`outboundOutcome=RETURNS`, distinct `returnReason`), one row with `outboundOutcome=STUCK_PENDING`, and a handful of `SETTLES` rows whose names span the **CoP** verdict space (exact-typing yields `MATCH`, near-typing yields `CLOSE_MATCH`, anything else yields `NO_MATCH` — derived by the existing `StubCopVerdictService` at lookup time, no new wiring required).

After this slice lands, the malai-api side is ready for the webapp PRD to consume: the read path exposes a stable Counterparty catalogue with deterministic outcomes; the write path lets a signed-in user trigger a credible inbound from any directory **Counterparty** to any of their own **Accounts**.

## User Stories

1. As a signed-in **User** loading the **Sandbox** surface in the webapp, I want `GET /api/v1/sandbox/counterparties` to return every fictional **Counterparty** the stub knows about, so that the Counterparties tab can be rendered without the webapp needing direct access to the stub's YAML.
2. As a signed-in **User** calling `GET /api/v1/sandbox/counterparties` without an `MalAccountHolderIdHeader`, I want the call to succeed (the catalogue is reference data shared across all holders), so that the endpoint matches its semantics and the webapp does not have to set a header it does not need.
3. As an unauthenticated client calling `GET /api/v1/sandbox/counterparties`, I want a `401 Unauthorized`, so that the Sandbox surface stays gated behind the bank login like the rest of `/api/v1/*`.
4. As a signed-in **User** calling `POST /api/v1/sandbox/inbound-payments` with a `creditedAccountId` that does not belong to my active **AccountHolder**, I want a `404 Not Found`, so that ownership enforcement matches every other holder-scoped endpoint and I cannot credit somebody else's account.
5. As a signed-in **User** calling `POST /api/v1/sandbox/inbound-payments` with a valid body, I want a `202 Accepted` and a new **Transaction** to appear in the credited Account's history within the existing scheme-message delay, so that the simulated inbound feels like a real inbound.
6. As a signed-in **User** double-clicking the "Simulate inbound" button (two identical POSTs with the same `Idempotency-Key`), I want exactly one **Transaction** created on the credited Account, so that an accidental retry does not produce duplicate credits.
7. As a signed-in **User** calling `POST /api/v1/sandbox/inbound-payments` without an `Idempotency-Key`, I want a `400 Bad Request`, so that the wire contract matches every other production write endpoint per workspace `CLAUDE.md`.
8. As a signed-in **User** calling `POST /api/v1/sandbox/inbound-payments` with `rail: "FPS"`, I want the resulting **Transaction** to be created with the FPS-shaped descriptor and to be visible immediately, so that the inbound is indistinguishable from a real-world FPS inbound on the recipient's account.
9. As a signed-in **User** calling `POST /api/v1/sandbox/inbound-payments` with `rail: "BACS"`, I want the resulting **Transaction** to be created with the BACS-shaped descriptor, so that I can demo BACS inbound visibility distinct from FPS.
10. As a signed-in **User** calling `POST /api/v1/sandbox/inbound-payments` with `rail` set to any value other than `FPS` or `BACS`, I want a `400 Bad Request`, so that unsupported rails are rejected at the boundary.
11. As a signed-in **User** calling `POST /api/v1/sandbox/inbound-payments` with `amountMinorUnits <= 0` or a missing required field (`creditedAccountId`, `originatorSortCode`, `originatorAccountNumber`, `originatorName`, `rail`, `amountMinorUnits`), I want a `400 Bad Request`, so that malformed inputs do not propagate to the stub.
12. As a signed-in **User** sending an outbound **Payment** to a sort-code + account-number that matches a directory **Counterparty** whose `outboundOutcome = SETTLES`, I want the payment to settle, so that the canonical happy path is deterministic-by-destination per ADR-0017 (webapp).
13. As a signed-in **User** sending an outbound **Payment** to a directory **Counterparty** whose `outboundOutcome = RETURNS` and `returnReason = INSUFFICIENT_FUNDS` (or any other `ReturnReason`), I want the payment to return with the matching reason, so that each return reason has a demonstrable destination in the catalogue.
14. As a signed-in **User** sending an outbound **Payment** to a directory **Counterparty** whose `outboundOutcome = STUCK_PENDING`, I want the payment to stay in `PENDING` forever (until the existing `demo-reset.sh` flow wipes-and-reseeds), so that I can demo the "in progress" state of a real-world payment.
15. As a signed-in **User** sending an outbound **Payment** to a sort-code + account-number that is *not* in the directory, I want the payment to settle, so that arbitrary destinations match the existing happy-path expectation and failure modes stay concentrated on the named fixtures (ADR-0017 webapp).
16. As a malai-stub maintainer at boot time, I want `StubFictionalDirectoryProperties` to fail-fast on any YAML row whose `outboundOutcome = RETURNS` has a null `returnReason`, or whose `outboundOutcome != RETURNS` has a non-null `returnReason`, so that config errors are caught at startup not at first payment.
17. As a malai-stub maintainer, I want the existing CoP verdict path (`POST /cop/lookup` against `StubCopVerdictService`) to be unchanged by this work, so that the dynamic name-vs-record matching (MATCH / CLOSE_MATCH / NO_MATCH derived by Levenshtein) keeps working untouched and the existing CoP tests do not regress.
18. As a sandbox-service maintainer, I want `sandbox-service` to depend only on `malai-stub-client` for outbound calls, and `malai-api` to depend only on `sandbox-service`, so that the inbound-to-stub HTTP direction stays encapsulated and `malai-api` stays a thin aggregator (per the user's pushback during design grilling).
19. As an oasdiff CI run, I want the `openapi.yaml` additions for `/api/v1/sandbox/counterparties` and `/api/v1/sandbox/inbound-payments` to be additive only, so that no consumer of the existing contract breaks.
20. As an operator running `demo-reset.sh` / `demo-reset-local.sh`, I want the existing seeder behaviour to clear the user's stuck PENDING Transactions and inbound-simulated Transactions transparently (they are normal rows on the existing tables), so that no new reset mechanism is needed for the Sandbox.

## Implementation Decisions

### Modules

- **`malai-services/sandbox-service/` — new service module.** Owns the two `/api/v1/sandbox/*` controllers, the `MalaiStubClient` configuration (wired from `malai-stub-client`), and the inbound-payment validator. Depends on `malai-account-client` for the holder-ownership check on `creditedAccountId`. Registered in root `pom.xml` `<modules>` and `<dependencyManagement>`. `malai-api` adds it as a dependency and imports its `@Configuration`.
- **`malai-clients/malai-stub-client/` — new client module.** Extends `AbstractMalaiClient`. Two methods: `getCounterparties(): CounterpartiesResponse` and `postInbound(rail, payload): Unit`. Configured from a new `malai.stub.base-url` property (Terraform writes this for deployed envs; local dev uses `http://localhost:<stub-port>`).
- **`malai-clients/malai-stub-client-test-support/` — new test-support module.** Provides `WithMalaiStub` interface with WireMock stubs (`getCounterpartiesReturns(...)`, `postInboundFpsReturns(...)`, `postInboundBacsReturns(...)`), plus `CounterpartiesResponseBuilder` + `CounterpartyResponseBuilder` matching the wire shape.
- **`malai-stub` — modified.** New controller `StubCounterpartiesController` with `GET /demo/counterparties` returning the directory rows. `StubAccount` widens to carry `outboundOutcome: OutboundOutcome` enum and the existing `returnReason: ReturnReason?`. New `StubFictionalDirectoryProperties.validate()` runs at boot and throws on invariant violations. New pure-Kotlin `OutboundOutcomeResolver` deep module — `(sortCode, accountNumber) → OutboundOutcome` — is used by both `StubFpsScheme` and `StubBacsScheme` (or wherever the BACS outbound handler lives — verify during implementation) when deciding what to emit after the submit ACK. The legacy `armReturn` global state stays in place but is *not* consulted on the new deterministic path; existing tests of `POST /demo/arm-return` continue to pass.
- **`malai-api` — modified.** Adds `sandbox-service` as a dependency in its POM; imports the service's `@Configuration`. Does not depend on `malai-stub-client` directly.

### API contract (in `openapi.yaml`, hand-authored first)

- **`GET /api/v1/sandbox/counterparties`** — auth required. Response `200 OK` with `CounterpartiesResponse: { counterparties: CounterpartyResponse[] }`. `CounterpartyResponse` carries `{ sortCode, accountNumber, bank, nameOnRecord, outboundOutcome, returnReason? }`. `OutboundOutcome` is a new enum schema `{ SETTLES | RETURNS | STUCK_PENDING }`; `Rail` is an existing enum or a new one carrying `{ FPS | BACS }` for the inbound path.
- **`POST /api/v1/sandbox/inbound-payments`** — auth required; requires `MalAccountHolderIdHeader` and `IdempotencyKeyHeader`. Request `InboundPaymentRequest: { rail, creditedAccountId, originatorSortCode, originatorAccountNumber, originatorName, amountMinorUnits, reference? }`. Response `202 Accepted` with no body. `404` on accountId mismatch; `400` on malformed body or missing required headers (idempotency-key validation goes through the existing `malai-commons` idempotency layer).

### Stub-side wire contract

- **`GET /demo/counterparties`** on the stub — returns the same shape as `CounterpartiesResponse` so the malai-api controller is a structural pass-through.
- **`POST /demo/inbound/{rail}`** on the stub — unchanged; reused as-is for the inbound forwarding.
- `outboundOutcome` enum widening on `StubAccount` is a config-shape change: every existing fixture row needs an `outboundOutcome` value (default `SETTLES` when migrating).

### Architectural framing

- **Backend ADR-0003** (already written) captures the dual role: `malai-stub` is now both the outside-world impersonator (SQS-out) *and* the demo-state introspector (HTTP-in for catalogue reads). The `CONTEXT-MAP.md` row is amended accordingly and `sandbox-service` is added as a new row.
- **Webapp ADR-0017** (already written) captures the destination-deterministic outcome decision driving the stub schema changes.
- The Sandbox catalogue is **static + global**: no DB, no Flyway migrations, no per-user persistence. The only stateful artefacts are normal **Transactions** on the existing tables.

### Authorisation

- Both endpoints follow the standard controller pattern: `@AuthenticationPrincipal user: MalaiUser` (or the narrower variants where applicable) and, for the inbound POST, ownership enforcement via `findByAccountIdAndAccountHolderId` style on the `creditedAccountId`.
- Idempotency follows the existing `malai-commons` idempotency layer for the inbound POST.

### Out-of-band wiring

- New env var / property `malai.stub.base-url` flows through `MalaiConfigurationProperties`; Terraform (`malai-infra`) needs to surface the stub's URL to `malai-api` in deployed envs. For local development, both `malai-api` and `malai-stub` already run on the dev box; the property points at the stub's local port.

## Testing Decisions

Tests cover external behaviour — request / response shape, side effects on the database / downstream HTTP, error verdicts — not internal call ordering or private state. The repo's BDD acceptance style (`given / when / then / and`) is the test shape; the global `/tdd` skill's examples translate to that DSL per `malai-backend/CLAUDE.md`.

- **`sandbox-service` acceptance tests (`@SpringTest` against the full Spring context, WireMock for the stub).** Kensa BDD. Cover: success cases for both endpoints; auth missing → 401; `MalAccountHolderIdHeader` missing on the inbound POST → 400; idempotency-key missing → 400; `creditedAccountId` belongs to another holder → 404; idempotent replay returns the same response and the stub is called exactly once; unsupported rail → 400; malformed body → 400; success-case response payload contains the expected props via `responseEntity().containsProp(json { ... noExtraProps() })`. Prior art: existing `auth-service`, `payee-service` controller tests.
- **`malai-stub-client` tests (`@MalaiClientSpringTest` loading `MalaiClientTestApplication`, WireMock).** Cover: request shape (HTTP method, URL, headers) for both methods; response deserialisation into the `CounterpartiesResponse` data class. Prior art: existing `malai-account-client`, `malai-account-holder-client` tests.
- **`malai-stub` `StubCounterpartiesController` acceptance test.** Loads the stub Spring context, asserts the directory rows are returned in the expected wire shape including the new `outboundOutcome` field. `StubFictionalDirectoryProperties` boot-time validation test — a malformed row (e.g. `outboundOutcome = RETURNS` with null `returnReason`) fails Spring context startup with a clear message.
- **`malai-stub` `OutboundOutcomeResolver` unit test** (or BDD test if the existing stub conventions call for it). Cover: known destination → matching enum; unknown destination → `SETTLES`. Pure unit; no Spring context.
- **`malai-stub` scheme integration test for `STUCK_PENDING`.** Sending an outbound to a stuck Counterparty leaves the payment in `PENDING` indefinitely (no settle, no return emitted on the SQS queue). Verify via the existing stub-scheme acceptance test pattern.
- **Existing CoP verdict tests** (`StubCopVerdictServiceTest`, `StubCopControllerTest`) — must continue to pass unchanged. The catalogue read does not touch the CoP path.
- **`armReturn` regression tests** — the legacy `POST /demo/arm-return` and `GET /demo/fps/returns` continue to work for backwards-compatibility consumers.

End-to-end coverage of the inbound flow (POST `/api/v1/sandbox/inbound-payments` → Transaction visible on `/api/v1/accounts/{id}/transactions`) is covered by the webapp's Playwright spec (see the companion webapp PRD). The backend slice does not need a `malai-end-to-end-tests` spec for v1 because the unit and acceptance tests above already exercise the boundary.

## Out of Scope

- **Per-user editable Counterparties.** The catalogue is static + global. No CRUD on Counterparties from the webapp. v2 if a demo demands it.
- **Per-payment manual unstick** for `STUCK_PENDING` Transactions. Clear via `demo-reset.sh` instead. v2 if a demo demands it.
- **Per-rail outcome variation on a single Counterparty.** `outboundOutcome` applies rail-agnostically. Future widening (`bacsOutcomeOverride?`) is additive.
- **Removal of the legacy `POST /demo/arm-return` / `GET /demo/fps/returns` routes.** They stay in place; the Sandbox path simply doesn't consult their state. A future cleanup ADR could remove them.
- **In-product reset endpoint** (`POST /api/v1/sandbox/reset`). Out of scope; `demo-reset.sh` covers the operational need.
- **CHAPS / SWIFT / SEPA rails on the inbound simulator.** v1 ships `FPS` and `BACS` only, matching the existing stub endpoints.
- **Removing the existing `EXTERNAL` accounts retired by the broader payments-service work.** That cleanup is owned by the `send-and-receive-money` PRD; this slice depends on it being already landed (verify during implementation).
- **UI surface, route shape, deep-link prefill plumbing.** All UI work is owned by the companion webapp PRD at `malai-webapp-v1/.scratch/sandbox/PRD.md`.

## Further Notes

- This PRD pairs with `malai-webapp-v1/.scratch/sandbox/PRD.md`. The backend slice should land first so the webapp regen has a populated `openapi.yaml` to consume.
- Architectural framing references: backend ADR-0003 (`malai-stub` gains demo-state-introspection role), webapp ADR-0016 (Sandbox placement), webapp ADR-0017 (destination-deterministic outcome). All three are written; read them before starting implementation.
- Per the workspace OpenAPI-first rule, edit `malai-api/src/main/resources/static/openapi.yaml` *first*, regenerate any webapp clients, then implement controllers to match. `oasdiff` in CI catches drift.
- The `CONTEXT-MAP.md` row for `malai-stub` was updated during design grilling to acknowledge its new dual role; the new `sandbox-service` row was added at the same time.
- The new wire `Counterparty` shape uses the **Counterparty** noun defined in `malai-webapp-v1/CONTEXT.md` (canonical for the webapp; cross-referenced from `malai-backend/CONTEXT-MAP.md`). The stub's internal type is still `StubAccount` — renaming it is out of scope; the wire/UI noun is what matters externally.
- The brief flagged "anything else you might think could be useful" — the sandbox surface is built to be extended additively (per-rail overrides, manual unstick, ad-hoc Counterparties) without breaking v1's contract.
