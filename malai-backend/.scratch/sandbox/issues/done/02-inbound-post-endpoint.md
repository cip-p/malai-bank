# Sandbox inbound POST endpoint end-to-end (openapi + stub client method + sandbox-service controller)

Status: ready-for-agent

## Parent

[`.scratch/sandbox/PRD.md`](../PRD.md)

## What to build

The write half of the Sandbox backend: a signed-in **User** can POST `/api/v1/sandbox/inbound-payments` to credit one of their active **AccountHolder**'s **Accounts** from a fictional **Counterparty**. The controller validates the request, enforces ownership of the credited Account, dedupes by `Idempotency-Key`, and forwards the payload to the stub's existing `POST /demo/inbound/{rail}` route via the established `malai-stub-client`.

End-to-end behaviour:
- Request shape: `{ rail: "FPS" | "BACS", creditedAccountId, originatorSortCode, originatorAccountNumber, originatorName, amountMinorUnits, reference? }`. Headers: `MalAccountHolderIdHeader`, `Idempotency-Key`.
- Response: `202 Accepted` with no body. The resulting **Transaction** appears on the credited Account via existing scheme-message plumbing — the webapp picks it up by re-querying `/api/v1/accounts/{id}/transactions` (cache invalidation lives in the webapp slice, not here).
- Ownership: the controller verifies `creditedAccountId` belongs to the active holder. Mismatch returns `404` (not `403`), matching the existing anti-enumeration policy used elsewhere in `malai-api`.
- Idempotency: handled at the `malai-commons` idempotency layer. The key is **not** forwarded to the stub — the stub has no idempotency layer of its own, and dedup at the malai-api boundary is sufficient since a single accepted POST results in exactly one stub call.
- Validation: `rail` must be `FPS` or `BACS`; unsupported values → `400`. `amountMinorUnits` must be `> 0`. Missing required fields → `400`.
- `malai-stub-client` gains a second method, `postInbound(rail, payload)`, posting to `/demo/inbound/fps` or `/demo/inbound/bacs` on the stub. The `-test-support` module gains the matching `postInboundFpsAccepts(...)` / `postInboundBacsAccepts(...)` WireMock fixtures.
- `openapi.yaml` is edited *first* with the new operation and request/response schemas (`InboundPaymentRequest`, `Rail` enum if not already present). `oasdiff` sees only additive changes.

## Acceptance criteria

- [ ] `openapi.yaml` adds the `/api/v1/sandbox/inbound-payments` POST operation and the new schemas (`InboundPaymentRequest`, `Rail` if new). The `MalAccountHolderIdHeader` and `IdempotencyKeyHeader` parameters are referenced from `#/components/parameters/...` (existing definitions). Response: `202` (no body), `400`, `404`. `oasdiff` against `main` passes with only additive changes.
- [ ] `MalaiStubClient` gains `postInbound(rail: Rail, payload: InboundPaymentForwardPayload): Unit`, posting to `/demo/inbound/fps` or `/demo/inbound/bacs` depending on rail. Body shape matches the existing stub `InboundRequest` data class.
- [ ] `malai-stub-client-test-support` `WithMalaiStub` interface gains `postInboundFpsAccepts(...)`, `postInboundBacsAccepts(...)` WireMock stubs.
- [ ] `sandbox-service` gains a controller method on `POST /api/v1/sandbox/inbound-payments`. Auth required via `@AuthenticationPrincipal user: MalaiUserWithAccountHolderId`. Body validated against the Kotlin request model. Ownership verified by looking up `creditedAccountId` via `malai-account-client` (or whatever the existing convention is for cross-service Account lookup) filtered by `user.accountHolderId`; not-found / not-owned → `NotFoundException`.
- [ ] On successful validation + ownership check, controller calls `MalaiStubClient.postInbound(rail, payload)` and returns `202 Accepted` with no body.
- [ ] Idempotency handled by the existing `malai-commons` idempotency mechanism; replay returns the same `202` response and the stub is called exactly once.
- [ ] Acceptance tests (`@SpringTest`, Kensa BDD) cover:
  - Success → `202`, stub called with the expected body via `madeTheHttpRequest(postRequestedFor(urlEqualTo("/demo/inbound/fps")))`.
  - Missing auth → `401`.
  - Missing `MalAccountHolderIdHeader` → `400`.
  - Missing `Idempotency-Key` → `400`.
  - `creditedAccountId` belongs to another holder → `404`.
  - `rail` other than `FPS`/`BACS` → `400`.
  - Malformed body (missing required field) → `400`.
  - `amountMinorUnits <= 0` → `400`.
  - Idempotent replay → `202` returned twice, stub called exactly once.
- [ ] Client tests (`@MalaiClientSpringTest`, WireMock): request URL + method + body shape asserted for both FPS and BACS calls.
- [ ] Existing `sandbox-service` GET tests from issue 01 still pass unchanged.
- [ ] `mvn install` from the repo root passes end-to-end.

## Blocked by

- [`.scratch/sandbox/issues/01-catalogue-get-end-to-end.md`](./01-catalogue-get-end-to-end.md) — needs the `malai-stub-client`, `sandbox-service` module skeleton, and the OpenAPI sandbox section to exist.
