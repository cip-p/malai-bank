# FPS inbound: admin endpoint on `malai-stub`, processor in `payments-service`, unknown-beneficiary auto-return

Status: ready-for-agent

## Parent

`.scratch/send-and-receive-money/PRD.md`

## What to build

The first **receive** flow. A demonstrator triggers an inbound FPS payment by hitting `POST /demo/inbound/fps` on `malai-stub` with a payload describing originator (external) and beneficiary (a Malai sort+account); the stub *blindly* publishes onto `fps.inbound.notify` (no validation that the beneficiary exists at malai — see PRD's "Demo mechanics"). `payments-service`'s `InboundPaymentProcessor` consumes the message and:

- Looks up the beneficiary by `(sort_code, account_number)` against the local `AccountIdentifierRepository`.
- **If found**: creates an `INBOUND` `Payment` row with `state = SETTLED`, populates debtor identifiers from the message, credits the beneficiary via `FPS_INBOUND_SUSPENSE` (suspense-debit + customer-credit pair), emits the `INBOUND_PAYMENT_RECEIVED` audit event. The recipient sees the credit in their account history immediately. No `PENDING` window from the recipient's POV — receipt is recognised at settlement, not earlier.
- **If not found**: publishes `FpsInboundReturned` onto `fps.inbound.returned` with reason `INVALID_ACCOUNT`. No `Payment` row is created malai-side; the message round-trips back to the stub for visibility. This exercises the realistic "garbled inbound" flow real banks handle.

The slice adds:

- New messages in `malai-payments-messages`: `FpsInboundNotify`, `FpsInboundReturned`.
- New queues provisioned in Localstack: `fps.inbound.notify`, `fps.inbound.returned`.
- New `FPS_INBOUND_SUSPENSE` system-owned `SUSPENSE` account seeded by a Flyway migration.
- New `InboundPaymentProcessor` deep module: encapsulates SQS-message-level idempotency (dedup by `endToEndId`), beneficiary resolution, the suspense → customer credit, and the unknown-beneficiary return publish.
- New `StubInboundEmitter` HTTP endpoint on `malai-stub`: `POST /demo/inbound/fps` with `{ beneficiarySortCode, beneficiaryAccountNumber, beneficiaryName, originatorSortCode, originatorAccountNumber, originatorName, amountMinorUnits, currency, description }`. The stub does not validate that the beneficiary exists (the point is to exercise the return-to-sender path).
- `PaymentLifecycleService.creditBeneficiary(inboundDto)` — the third operation on the lifecycle service, alongside `transitionToSettled` and `transitionToReturned`. Encapsulates the credit posting + audit event for `INBOUND` payments.

Audit chain emits the new `INBOUND_PAYMENT_RECEIVED` action code. The chain-lock posture is unchanged.

The ambient-activity scheduler (the `@Scheduled` salary-day job inside `malai-stub`) is **deferred to #08** — it pairs more naturally with the BACS-Direct-Credit work for "salary lands on payday." This slice ships *only* the admin-endpoint inbound trigger.

Domain glossary: `Payment` (with `direction = INBOUND`), `Suspense account`, `Settlement`, `Return`.

## Acceptance criteria

- [ ] New Flyway migration seeds `FPS_INBOUND_SUSPENSE` as a System-owned `SUSPENSE` account, filtered from `GET /api/v1/accounts`.
- [ ] `malai-payments-messages` gains `FpsInboundNotify` and `FpsInboundReturned` data classes.
- [ ] Localstack provisions `fps.inbound.notify` and `fps.inbound.returned` queues on start.
- [ ] `POST /demo/inbound/fps` on `malai-stub`: validates payload shape only, publishes one `FpsInboundNotify` onto `fps.inbound.notify`, returns 204. No existence check on the beneficiary.
- [ ] `InboundPaymentProcessor` consumes one message and either:
  - creates an `INBOUND` `Payment` row (`state = SETTLED`), writes one `TransactionEntity` with two ledger entries (suspense-debit + beneficiary-credit), emits `INBOUND_PAYMENT_RECEIVED` audit event; the recipient's account history shows the credit with originator details; **or**
  - publishes `FpsInboundReturned` with `INVALID_ACCOUNT` if the beneficiary is unknown; no `Payment` row is created.
- [ ] Duplicate `FpsInboundNotify` messages (same `endToEndId`) are processed exactly once.
- [ ] Acceptance tests cover: known-beneficiary happy path end-to-end (admin endpoint hit → recipient's account history shows credit), unknown-beneficiary auto-return end-to-end (admin endpoint hit → `fps.inbound.returned` message observable from the stub side), duplicate-message dedup.
- [ ] `malai-stub` tests cover the admin endpoint's payload validation and the publish behaviour.
- [ ] `GET /api/v1/payments/{id}` returns the inbound payment for the beneficiary's `account_holder_id`. (Inbound payments are visible to the beneficiary; the originator's side is the external bank's concern.)
- [ ] `openapi.yaml` updated: `Payment` response schema documents the `direction` field; debtor identifiers populated on inbound responses.
- [ ] `mvn install` passes.

## Blocked by

`.scratch/send-and-receive-money/issues/02-fps-outbound-happy-path-malai-to-malai.md`
