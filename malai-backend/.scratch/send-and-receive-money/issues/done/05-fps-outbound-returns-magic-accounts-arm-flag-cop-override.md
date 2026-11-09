# FPS outbound returns: reason codes, magic accounts, arm-flag endpoint, CoP-override auto-mismatch

Status: ready-for-agent

## Parent

`.scratch/send-and-receive-money/PRD.md`

## What to build

Complete the FPS-outbound *sad path* surface so a demonstrator can trigger any return scenario on cue. After this slice ships:

1. **Magic accounts.** `StubFictionalDirectory` is extended with a small set of always-returns accounts, each pinned to a specific `Return reason`. Examples: "Jane Closed" at the NatWest sort code → `ACCOUNT_CLOSED`; "John Deceased" at HSBC → `BENEFICIARY_DECEASED`; "Bank Down PLC" at a separate sort code → `RECIPIENT_BANK_UNAVAILABLE`. CoP for these accounts still returns `MATCH` (they're real entries in the stub directory) — only the FPS submission triggers the return. The exact names + sort+account combinations live in the same configurable resource that powers the rest of the fictional directory.
2. **Arm-flag endpoint.** `POST /demo/arm-return { reason: <ReturnReason> }` on `malai-stub`. Arms a one-shot flag inside the stub; the next consumed `FpsOutboundSubmit` ignores the destination, publishes `fps.outbound.returned` with the armed reason, and clears the flag. Subsequent submits resume normal behaviour. The flag is stored in-memory (single stub instance is fine for v1; arm-flag semantics are deliberately not distributed).
3. **CoP-override auto-mismatch.** `POST /api/v1/payments` accepts a `copVerdictOverride: true` field, set by the webapp when the customer pressed-through a `NO_MATCH` / `CLOSE_MATCH` warning. The flag is recorded on the `Payment` row and propagated on the `FpsOutboundSubmit` message. `StubFpsScheme`, when it sees the flag, publishes `fps.outbound.returned` with `ACCOUNT_NAME_MISMATCH` regardless of destination. This is the showcase educational moment: the rail itself rejects the payment that CoP warned about, even after the user clicked through.

All three triggers funnel through the same `fps.outbound.returned` message and the same `PaymentLifecycleService.transitionToReturned` codepath. The slice extends the `Return reason` enum to its full v1 set: `ACCOUNT_CLOSED`, `ACCOUNT_NAME_MISMATCH`, `RECIPIENT_BANK_UNAVAILABLE`, `BENEFICIARY_DECEASED`, `INVALID_ACCOUNT` (the last one already wired in #04). The webapp's transaction list / payment detail surfaces are out of scope; this slice ends at the API + payment row + audit event.

The auto-mismatch priority order inside `StubFpsScheme` is:

1. `copVerdictOverride = true` → always return `ACCOUNT_NAME_MISMATCH` (regardless of destination known/unknown to stub).
2. Otherwise, if arm-flag is set → return with the armed reason; clear the flag.
3. Otherwise, if destination is a magic account → return with the magic-account-assigned reason.
4. Otherwise, if destination is in `StubFictionalDirectory` → settle.
5. Otherwise → return `INVALID_ACCOUNT` (existing #04 behaviour).

That order matters: the CoP-override path is the headline demo, so it wins; the arm-flag is the demonstrator's manual override and beats magic accounts; magic accounts cover scripted demo runs.

Audit chain emits `PAYMENT_RETURNED` with the reason in `afterState`. The chain-lock posture is unchanged.

Domain glossary: `Return`, `Return reason`, `Lifecycle`, `Stub`.

## Acceptance criteria

- [ ] `Return reason` enum lands with all five v1 values; persisted as `payments.return_reason` `VARCHAR`.
- [ ] `StubFictionalDirectory` resource includes the magic-account entries; one per reason code in the enum (except `INVALID_ACCOUNT`, which is "no entry at all").
- [ ] `POST /demo/arm-return` accepts a body with `{ reason: <ReturnReason> }`; arms a one-shot flag. Returns 204 on success. The next outbound payment after the arm returns with the configured reason and clears the flag.
- [ ] `PaymentRequest` schema gains an optional `copVerdictOverride: boolean` field (defaults to `false`). Recorded on `Payment` row; propagated on `FpsOutboundSubmit` message.
- [ ] `StubFpsScheme` honours the priority order documented above. The order is exercised by tests, including: arm-flag overrides magic-account; CoP-override overrides arm-flag; CoP-override overrides magic-account.
- [ ] CoP-override auto-mismatch produces `ACCOUNT_NAME_MISMATCH` even when destination is a stub-recognised account with a *matching* name — the override flag means "user pressed-through despite a warning", and that's the signal.
- [ ] Acceptance tests in `payments-service` cover: each return reason end-to-end (POST → PENDING → RETURNED with reason → audit row → customer credited back), CoP-override auto-mismatch end-to-end, no double-credit on duplicate `fps.outbound.returned` messages.
- [ ] `malai-stub` tests cover the priority order explicitly with a matrix.
- [ ] `openapi.yaml` updated: `PaymentRequest.copVerdictOverride` documented; `PaymentResponse`/`GET` exposes `returnReason` on returned payments; `ReturnReason` enum schema added.
- [ ] `mvn install` passes.

## Blocked by

`.scratch/send-and-receive-money/issues/04-fps-outbound-to-non-malai-uk-destinations.md`
