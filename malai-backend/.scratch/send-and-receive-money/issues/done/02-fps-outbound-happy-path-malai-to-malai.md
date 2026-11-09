# FPS outbound happy path to another Malai customer (introduces SQS, Localstack, `malai-stub`, suspense, lifecycle)

Status: ready-for-agent

## Parent

`.scratch/send-and-receive-money/PRD.md`

## What to build

The first end-to-end FPS payment: an authenticated Malai customer pays *another* Malai customer by sort-code + account-number + name via `POST /api/v1/payments`. The payment ride the **FPS** rail — not the on-us shortcut. The customer's debit lands immediately, the **`FPS_OUTBOUND_SUSPENSE`** account is credited, an `fps.outbound.submit` SQS message is published, the `malai-stub` deployable consumes it, publishes `fps.outbound.settled`, `payments-service` consumes that and transitions the `Payment` from `PENDING` to `SETTLED` (the suspense debit + scheme-settlement credit pair is the second ledger transaction on the payment). The recipient's account is credited as part of the same settle path. From the API caller's perspective: the initial `POST` returns `state = PENDING` within milliseconds; a subsequent `GET /api/v1/payments/{id}` shows `SETTLED` within ~1 simulated second.

This slice is necessarily thick because it introduces the infrastructure the rest of the build depends on:

- **`malai-stub` Maven module** — new top-level Spring Boot deployable (alongside `malai-api`, `malai-services`, etc.), explicitly **not** declared as a dependency of `malai-api`. Has its own `application.yaml`, its own `Dockerfile`, its own start-up profile for Localstack vs real SQS.
- **`malai-clients/malai-payments-messages`** — pure Kotlin data-class module for the SQS wire payloads (`FpsOutboundSubmit`, `FpsOutboundSettled`). No logic. Depended on by both `payments-service` and `malai-stub`.
- **`malai-commons/malai-commons`** gains a `MalaiSqsConfigurationProperties` + AWS SDK v2 SQS client wiring; profile-aware (`local` → Localstack endpoint, real AWS otherwise).
- **`start-docker-containers-local.sh`** brings up Localstack alongside Postgres + WireMock. Queues are provisioned on container start; wipe-on-rerun semantics preserved.
- **`AccountType.SUSPENSE`** added to the enum. New Flyway migration introduces `FPS_OUTBOUND_SUSPENSE` and `SCHEME_SETTLEMENT` accounts seeded as System-owned `SUSPENSE` rows, filtered out of `GET /api/v1/accounts`.
- **`PaymentLifecycleService`** — the deep module with `transitionToSettled(paymentId)`. Encapsulates state-machine guards (terminal-transition re-application is a no-op), the paired ledger postings, the audit event, and the suspense balance accounting.
- **`FpsScheme`** interface + `SqsFpsScheme` implementation. Single seam where a real Pay.UK client would slot in.
- **`StubFpsScheme`** inside `malai-stub` — first iteration: consumes `fps.outbound.submit` and unconditionally publishes `fps.outbound.settled` (no returns, no magic accounts yet; those land in #05).

For *this* slice, the only payable destinations are other malai customers (resolvable in the local directory via `AccountIdentifierRepository.findByIdentifierTypeAndBankIdentifierAndAccountIdentifier`). External destinations still return `BAD_REQUEST` with "rail not yet supported"; those open up in #04.

Routing logic in `payments-service`: source + destination both resolve to malai accounts → if same `account_holder_id` route as Book Transfer (per #01), else route as FPS. Currency must be GBP for both endpoints (cross-currency is out of scope for FPS).

Audit chain gains the new `PAYMENT_INITIATED` and `PAYMENT_SETTLED` action codes. The chain-lock posture is unchanged.

Domain glossary terms used: `Payment`, `Rail`, `FPS`, `Suspense account`, `Settlement`, `Lifecycle`, `Stub`, `Queue`, `Scheme`, `Available balance`, `Settled balance`.

## Acceptance criteria

- [ ] New Maven modules registered: `malai-stub` (top-level, **not** a dependency of `malai-api`), `malai-clients/malai-payments-messages`.
- [ ] Localstack added to `start-docker-containers-local.sh`; queues `fps.outbound.submit` and `fps.outbound.settled` provisioned on start; wipe-on-rerun matches Postgres semantics.
- [ ] CI's docker stack gains a Localstack container; tests that need SQS use it (no testcontainers per-test if the shared container suffices, matching the existing shared-Postgres pattern).
- [ ] `AccountType.SUSPENSE` exists; the enum value is filtered from `GET /api/v1/accounts` (covered by tests).
- [ ] New Flyway migration seeds `FPS_OUTBOUND_SUSPENSE` and `SCHEME_SETTLEMENT` as System-owned `SUSPENSE` accounts. V14's per-merchant `EXTERNAL` rows are **left in place** for now — they're retired in #09.
- [ ] `PaymentLifecycleService.transitionToSettled` writes a second `TransactionEntity` with the suspense-debit + scheme-settlement-credit pair; sum-to-zero per `transaction_id` holds for both the initial PENDING transaction and the SETTLED transaction. Re-applying the transition to an already-`SETTLED` payment is a no-op (not an exception).
- [ ] `SqsFpsScheme.submit(payment)` publishes exactly one `FpsOutboundSubmit` message onto `fps.outbound.submit`, carrying `paymentId`, `endToEndId`, debtor + beneficiary identifiers, amount, currency, description. Dedup by `paymentId` — re-submitting the same payment is a no-op.
- [ ] `StubFpsScheme` consumes `fps.outbound.submit` from Localstack and publishes `FpsOutboundSettled` onto `fps.outbound.settled`. First iteration: always settles, never returns. Idempotent on `endToEndId`.
- [ ] `payments-service`'s settled-consumer transitions the `Payment` to `SETTLED` and emits the `PAYMENT_SETTLED` audit event within the same DB transaction. SQS message dedup by `(messageType, endToEndId)`.
- [ ] An authenticated FPS send from customer A to customer B: the initial response is `state = PENDING`; a `GET /api/v1/payments/{id}` ~1s later returns `state = SETTLED`; the recipient's account history shows the credit; sender's `Available balance` reflects the debit immediately, `Settled balance` at settle time.
- [ ] Acceptance tests: happy FPS A→B, idempotency replay of the initial POST, SQS-message dedup of duplicate settled messages, the unknown-destination "rail not yet supported" path is unchanged for non-malai destinations.
- [ ] `malai-stub` has its own test suite with WireMock-style fixtures or a small `@MalaiStubSpringTest` annotation covering the always-settle behaviour.
- [ ] `malai-commons/malai-commons` SQS configuration is `MalaiHttpClientCustomizer`-style — no bean overriding, profile-aware (`local` for Localstack endpoint, default for AWS), and exposes an `SqsClient` that the rest of the stack consumes.
- [ ] `openapi.yaml` updated: response now describes the `PENDING`/`SETTLED`/`RETURNED` lifecycle values returned by `GET /api/v1/payments/{paymentId}`.
- [ ] `mvn install` from the repo root passes — including the new `malai-stub` module's tests.

## Blocked by

`.scratch/send-and-receive-money/issues/01-payments-service-foundation-and-book-transfer.md`
