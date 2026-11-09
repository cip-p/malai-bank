# FPS outbound to non-Malai UK destinations (happy path)

Status: ready-for-agent

## Parent

`.scratch/send-and-receive-money/PRD.md`

## What to build

Open up FPS to genuinely *external* UK destinations — sort+account-number pairs at the fictional NatWest/HSBC/Lloyds/Monzo/Barclays accounts in `StubFictionalDirectory`. After this slice ships, a Malai customer can call `POST /api/v1/payments` with a destination that doesn't exist in the local directory; CoP federation (#03) answers `MATCH` against the stub; the payment goes `PENDING` against `FPS_OUTBOUND_SUSPENSE`; the stub consumes the submit message, recognises the destination, and settles. The `Payment` row records the external destination as `(beneficiary_sort_code, beneficiary_account_no, beneficiary_name)` with `beneficiary_account_id = NULL` — destination-on-the-transaction, not destination-as-a-row-in-accounts (per root ADR-0002).

The routing change in `payments-service`:

1. Local directory lookup for the destination (existing `AccountIdentifierRepository`).
2. If found AND same `account_holder_id` → Book Transfer (per #01).
3. If found AND different `account_holder_id` → FPS to malai customer (per #02).
4. **If not found locally → FPS to external; record beneficiary identifiers on the `Payment` row; debit source + credit `FPS_OUTBOUND_SUSPENSE`; publish `fps.outbound.submit`.** This is the new path.

`StubFpsScheme` learns to handle external destinations: it consults `StubFictionalDirectory` for the (sort, account) on the submit message; if found, publish `fps.outbound.settled` as today; if not found, publish `fps.outbound.returned` with `INVALID_ACCOUNT` (a *minimal* return path — the full return surface lands in #05 with magic accounts and arm-flag and CoP-override; what this slice adds is the trivial "destination doesn't exist anywhere" return).

This means this slice technically introduces the *first* `RETURNED` transition — but driven only by "the stub has no record of this account at all," not by any demo affordance. `PaymentLifecycleService.transitionToReturned(paymentId, reason)` lands here with `INVALID_ACCOUNT` as the only reason wired. The richer return-trigger surface in #05 layers on top.

The CoP `copVerdictOverride` flag is **not** consulted by the stub in this slice — that wiring is part of #05's CoP-override auto-mismatch. Customers who override CoP and send to a stub-known destination still settle here; that gap is intentional and closes in #05.

Domain glossary: `Payment`, `Rail`, `FPS`, `Suspense account`, `Return`, `Return reason`. The phrase "bank's directory" is now intentionally federated — covered by ADR-0001 (account-service).

## Acceptance criteria

- [ ] `payments-service` routing accepts destinations not in the local directory and routes them to FPS-external.
- [ ] `Payment` row populated with `(beneficiary_sort_code, beneficiary_account_no, beneficiary_name)` and `beneficiary_account_id = NULL` for external destinations.
- [ ] `FpsOutboundSubmit` message carries the external beneficiary identifiers; `StubFpsScheme` consults `StubFictionalDirectory` to decide settle vs. return-as-invalid.
- [ ] `PaymentLifecycleService.transitionToReturned(paymentId, INVALID_ACCOUNT)` exists and behaves correctly: writes a second `TransactionEntity` with the suspense-debit + customer-credit pair (sender's original debit is reversed); `Payment` moves `PENDING → RETURNED`; `return_reason = INVALID_ACCOUNT`; audit emits `PAYMENT_RETURNED`.
- [ ] Available balance returns to the pre-send value after a return; settled balance is unchanged.
- [ ] CoP federation (#03) and FPS-external routing wire up correctly: a UI request that first calls `/api/v1/confirmation-of-payee` and then `/api/v1/payments` against the same external pair produces a consistent outcome (CoP answers from stub, FPS routes to stub, stub settles).
- [ ] Acceptance tests: happy FPS-external A→{NatWest stub account}, FPS-external against a sort+account neither malai nor stub knows → `RETURNED` with `INVALID_ACCOUNT`, idempotent settled-consumer on the success path, idempotent returned-consumer on the failure path.
- [ ] `StubFpsScheme` tests cover: known stub destination settles, unknown destination returns `INVALID_ACCOUNT`. `copVerdictOverride` is not yet honoured (test asserts current behaviour; #05 will change it).
- [ ] `mvn install` passes.

## Blocked by

`.scratch/send-and-receive-money/issues/02-fps-outbound-happy-path-malai-to-malai.md`,
`.scratch/send-and-receive-money/issues/03-cop-federates-to-malai-stub.md`
