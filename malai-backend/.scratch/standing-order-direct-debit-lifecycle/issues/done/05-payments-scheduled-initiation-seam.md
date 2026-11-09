# 05 — Payments accepts scheduled initiations (internal seam)

Status: done

## Parent

`.scratch/standing-order-direct-debit-lifecycle/PRD.md`

## What to build

The payments half of the standing-order tracer: an internal, service-token-authenticated initiation endpoint through which a scheduler can execute an occurrence as a real outbound payment. It reuses the existing initiation logic — rail routing (FPS with hold and async settlement for external counterparties, book transfer on-us), currency guards, audit — with one addition: the caller supplies the deterministic per-occurrence end-to-end ID, and a duplicate ID is a harmless idempotent bounce off the existing unique key rather than a second payment (per ADR 0004, the bounce is the mechanism). A new payments client module pair (production client plus test support with builders) gives owning services their way in, per the module-pair convention.

## Acceptance criteria

- [x] Internal initiation endpoint authenticated like the existing internal endpoints; not reachable with a customer token
- [x] FPS path: hold placed on the payer (asserted against the WireMock'd ledger), outbound command published (asserted as a real SQS message), payment recorded PENDING with the caller's end-to-end ID — full-entity database assertion
- [x] On-us destination routes as a book transfer and settles synchronously, reusing existing routing
- [x] Re-initiation with an already-used end-to-end ID creates no second payment, publishes no second command, and reports success referencing the existing payment
- [x] Insufficient available funds surfaces as a distinct failure the caller can recognise as a missed occurrence
- [x] New payments client module pair registered per the module-pair convention, with the standard WireMock-backed request-construction and response-deserialisation tests and test-support builders
- [x] Kensa acceptance tests in the payments service's existing style, message-level assertions throughout

## Progress

2026-07-07 — backend slice landed on `main`. `POST /api/v1/internal/payments`, service-token
authed (`@InternalEndpoint` + `@PreAuthorize` on the shared `malai-internal/onboarding.write`
scope, like the account/ledger internal endpoints); a customer token has no `client_id` claim so
the `ServiceJwtFilter` rejects it (401). The request carries the caller-supplied `endToEndId`,
`accountHolderId`, source account, payee snapshot (sort code / account number / name), amount,
GBP currency and reference. `PaymentsService.initiatePayment` was refactored to share a private
`initiate(...)` core with the new `initiateScheduledPayment(...)`; the customer path is unchanged
(actor CUSTOMER, generated e2e id) while the scheduled path stamps actor SYSTEM and the supplied
e2e id. Idempotent bounce is a pre-check on `PaymentRepository.findByEndToEndId` (returns the
existing payment, no second hold/command/audit) — the schedulers fire sequentially so a pre-check
is sufficient; the unique key remains the backstop (ADR 0004). Insufficient funds surfaces as a
400 `PAYMENT_INSUFFICIENT_FUNDS` (7983) which the client maps to a distinct
`PaymentInsufficientFundsException` (mirrors `LedgerInsufficientFundsException`) so issue 06's
executor can recognise a missed occurrence.

The `malai-payments-client` pair already existed (customer-facing `initiatePayment`); extended it
with `initiateScheduledPayment(serviceToken, request)` + `InternalInitiatePaymentRequest` +
`PaymentInsufficientFundsException`, plus `InternalInitiatePaymentRequestBuilder` and
`initiateScheduledPayment*` WireMock stubs in the test-support. Added an explicit `malai-commons`
dependency to the client pom (needed for `MalaiCode`, mirroring `malai-ledger-client`).

Full-entity DB assertion is done field-by-field inside `thePaymentInTheDatabase(id) { ... }` — the
service's established seam — rather than `shouldBe PaymentEntity(...)`, because `traceId` is a
dynamic per-request value; every meaningful column is asserted.

No OpenAPI change: `/api/v1/internal/**` is explicitly excluded from the hand-authored contract, so
oasdiff stays clean. No migration needed. `mvn install` green.

Consumed by issue 06 (standing-order executor), which adds the standing-order service-token client
and the per-minute due-scan that calls `initiateScheduledPayment`.

## Blocked by

None - can start immediately
