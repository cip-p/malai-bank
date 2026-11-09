# Sandbox catalogue GET end-to-end (stub schema + stub route + client pair + sandbox-service + openapi)

Status: done

## Parent

[`.scratch/sandbox/PRD.md`](../PRD.md)

## What to build

The first tracer bullet through the Sandbox backend: a signed-in **User**'s `GET /api/v1/sandbox/counterparties` call returns the stub's fictional **Counterparty** catalogue. Everything below this seam — schema, stub route, client pair, service module, OpenAPI contract — is established here so the inbound endpoint (issue 02) and the stub scheme outcome wiring (issue 03) can plug in on top.

End-to-end behaviour:
- Stub starts up. `StubFictionalDirectoryProperties` validates fixtures at boot — every row carries the new `outboundOutcome` enum, and `returnReason` is non-null iff `outboundOutcome = RETURNS`.
- Stub exposes `GET /demo/counterparties` returning the directory rows in the wire shape `{ counterparties: [{ sortCode, accountNumber, bank, nameOnRecord, outboundOutcome, returnReason? }] }`.
- New `malai-clients/malai-stub-client` + `-test-support` module pair extends `AbstractMalaiClient`, configured from `malai.stub.base-url`, exposes `getCounterparties()` only (the `postInbound()` method ships in issue 02).
- New `malai-services/sandbox-service` module exposes `GET /api/v1/sandbox/counterparties` (auth-required, no `MalAccountHolderIdHeader`) and is a structural pass-through to `MalaiStubClient.getCounterparties()`.
- `malai-api` aggregates `sandbox-service` (POM dependency + `@Configuration` import). It does **not** depend on `malai-stub-client` directly — that dependency lives inside `sandbox-service` per the user's pushback during design grilling (captured in [ADR-0003](../../../docs/adr/0003-malai-stub-gains-demo-state-introspection-role.md)).
- `openapi.yaml` is edited *first* (per workspace OpenAPI-first rule) with the new endpoint and the new schemas (`CounterpartiesResponse`, `CounterpartyResponse`, `OutboundOutcome` enum). `oasdiff` in CI sees only additive changes.

Fixture content: enough rows that the catalogue covers every behaviour the Sandbox surface will demo — at minimum one row per `ReturnReason` value (with `outboundOutcome=RETURNS`), one row with `outboundOutcome=STUCK_PENDING`, and a handful of `outboundOutcome=SETTLES` rows whose `nameOnRecord` values give the unguided prospect easy MATCH / CLOSE_MATCH / NO_MATCH targets. Each row's `bank` should be a recognisable UK fictional name; sort codes and account numbers should not collide with the existing seeded customer accounts.

The legacy `POST /demo/arm-return` and `GET /demo/fps/returns` stub routes are not touched by this slice. The new `outboundOutcome` field is wired through *for the catalogue read* only; the FPS / BACS scheme handlers do not consult it yet (that wiring is issue 03).

## Acceptance criteria

- [ ] `StubAccount` widens to carry `outboundOutcome: OutboundOutcome` (enum: `SETTLES | RETURNS | STUCK_PENDING`); existing `returnReason: ReturnReason?` is retained.
- [ ] `StubFictionalDirectoryProperties` validates at boot — context startup fails fast with a clear message if any row has `outboundOutcome = RETURNS` with null `returnReason`, or `outboundOutcome != RETURNS` with non-null `returnReason`.
- [ ] Stub YAML fixtures include: one row per `ReturnReason` value (`outboundOutcome=RETURNS`); one row with `outboundOutcome=STUCK_PENDING`; at least three rows with `outboundOutcome=SETTLES` whose `nameOnRecord` values are usable for CoP MATCH / CLOSE_MATCH demos.
- [ ] New stub controller `StubCounterpartiesController` exposes `GET /demo/counterparties` returning every directory row in the wire shape `{ counterparties: [{ sortCode, accountNumber, bank, nameOnRecord, outboundOutcome, returnReason? }] }`. `returnReason` is omitted from the JSON when null.
- [ ] New module pair `malai-clients/malai-stub-client` and `malai-clients/malai-stub-client-test-support` registered in root `pom.xml` (`<modules>` and `<dependencyManagement>`). Client extends `AbstractMalaiClient`. Configured from `malai.stub.base-url`. Exposes only `getCounterparties()` in this slice.
- [ ] `malai-stub-client-test-support` provides `WithMalaiStub` Kensa fixture interface with `getCounterpartiesReturns(...)` WireMock stub, plus `CounterpartiesResponseBuilder` + `CounterpartyResponseBuilder` (TestBuilder pattern).
- [ ] New service module `malai-services/sandbox-service` registered in root `pom.xml`. Exposes `GET /api/v1/sandbox/counterparties` (auth required, no `MalAccountHolderIdHeader`). Controller uses `@AuthenticationPrincipal user: MalaiUser`. Pass-through to `MalaiStubClient.getCounterparties()`.
- [ ] `malai-api` POM adds `sandbox-service` as a dependency and imports its `@Configuration`. `malai-api` has no direct dependency on `malai-stub-client`.
- [ ] `openapi.yaml` adds the `/api/v1/sandbox/counterparties` operation and the schemas (`CounterpartiesResponse`, `CounterpartyResponse`, `OutboundOutcome`). Edits land *before* the controllers; `oasdiff` against `main` passes with only additive changes.
- [ ] `sandbox-service` acceptance tests (`@SpringTest`, Kensa BDD): success returns the catalogue with all expected props via `responseEntity().containsProp(json { ... noExtraProps() })`; unauthenticated → 401; the call to the stub is verified via `madeTheHttpRequest(getRequestedFor(urlEqualTo("/demo/counterparties")))`.
- [ ] `malai-stub-client` tests (`@MalaiClientSpringTest`, WireMock): request URL + method asserted; response deserialises into `CounterpartiesResponse` with all expected fields.
- [ ] Stub `StubCounterpartiesController` acceptance test: returns the configured fixtures in the wire shape.
- [ ] Stub boot-validation test: a malformed fixture row fails context startup with a recognisable message.
- [ ] Existing CoP tests (`StubCopVerdictServiceTest`, `StubCopControllerTest`) still pass unchanged.
- [ ] Existing `armReturn` tests (`StubFpsDemoControllerTest`, `StubInboundDemoControllerTest`) still pass unchanged.
- [ ] `mvn install` from the repo root passes end-to-end.

## Blocked by

None — can start immediately.
