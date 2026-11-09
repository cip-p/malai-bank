# BACS-Direct-Credit inbound stub + ambient activity scheduler (salary day)

Status: ready-for-agent

## Parent

`.scratch/send-and-receive-money/PRD.md`

## What to build

Inbound salary-style credits via a BACS-Direct-Credit-shaped stub rail, plus the ambient-activity scheduler inside `malai-stub` that fires recurring inbound credits on a configured cadence. After this slice ships, a demonstrator either (a) triggers a one-off BACS inbound through an admin endpoint, or (b) runs the demo with the ambient scheduler enabled and sees a "salary" arrive on the 25th of each simulated month for seeded customer accounts.

The slice adds:

- New message in `malai-payments-messages`: `BacsInboundNotify`. Same shape as `FpsInboundNotify` but with a `rail = BACS_DIRECT_CREDIT` discriminator. Same beneficiary identifier shape (sort + account-number — BACS is UK-only).
- New queue in Localstack: `bacs.inbound.notify`.
- New `BACS_INBOUND_SUSPENSE` system-owned `SUSPENSE` account seeded by a Flyway migration.
- New admin endpoint on `malai-stub`: `POST /demo/inbound/bacs` — same payload shape as the FPS variant in #06 (originator + beneficiary + amount + currency + description), publishes one `BacsInboundNotify` onto `bacs.inbound.notify`. Blind-publish; no beneficiary existence check.
- `InboundPaymentProcessor` in `payments-service` (introduced in #06) is extended to consume `bacs.inbound.notify` *in addition* to `fps.inbound.notify`. The processor switches on the rail discriminator, credits via `BACS_INBOUND_SUSPENSE` instead of `FPS_INBOUND_SUSPENSE`, and records the `Payment` with `rail = BACS_DIRECT_CREDIT`. The state machine, audit code (`INBOUND_PAYMENT_RECEIVED`), and unknown-beneficiary auto-return logic are identical to FPS inbound — only the rail and the suspense account change.
- **`StubAmbientActivityScheduler`** inside `malai-stub` — the new `@Scheduled` job. Reads an `ambient-payroll.yaml` (or similar) resource at startup describing recurring credits: `{ beneficiarySortCode, beneficiaryAccountNumber, originatorName, amountMinorUnits, dayOfMonth, description }`. On its configured cadence (cron-shaped), publishes the appropriate `BacsInboundNotify` messages. ShedLock-guarded for cluster safety even though the stub is a single instance in v1.
- The scheduler is **off by default** in test profiles; on by default in `local` and AWS profiles. Tests that want to exercise it enable it explicitly.

The slice **does not** touch:

- Inbound *international* (out of scope for v1).
- BACS Direct *Debit* outbound (still out of scope; the existing `direct-debit-service` remains read-only).
- Outbound BACS Direct Credit (not in the brief).

Domain glossary: `BACS Direct Credit` is a new term — added to `payments-service/CONTEXT.md` as part of this slice. The avoidance note: don't conflate with `Direct Debit` (different scheme, different direction, different mandate model).

## Acceptance criteria

- [ ] New Flyway migration seeds `BACS_INBOUND_SUSPENSE`. Filtered from `GET /api/v1/accounts`.
- [ ] `malai-payments-messages` gains `BacsInboundNotify`. Localstack provisions `bacs.inbound.notify`.
- [ ] `POST /demo/inbound/bacs` on `malai-stub` validates payload shape only, publishes one `BacsInboundNotify`, returns 204.
- [ ] `InboundPaymentProcessor` handles both `fps.inbound.notify` and `bacs.inbound.notify`; produces `Payment` rows with the correct `rail` value; the suspense account used matches the rail.
- [ ] Acceptance tests cover: BACS-DC happy path end-to-end via the admin endpoint, unknown-beneficiary auto-return on the BACS path, no cross-rail contamination (an FPS-inbound message does not write to the BACS suspense and vice versa).
- [ ] `StubAmbientActivityScheduler` reads its configuration resource at startup; running with the scheduler enabled and a virtual clock advanced to the configured day-of-month produces the expected messages on `bacs.inbound.notify`.
- [ ] Scheduler is off by default in the test profile; tests can opt in via a profile property.
- [ ] `malai-stub` tests cover: admin-endpoint publish path, scheduler-driven publish path with mocked clock.
- [ ] `payments-service/CONTEXT.md` updated with the `BACS Direct Credit` term and its avoidance note.
- [ ] `openapi.yaml` unchanged (BACS inbound is a stub-side trigger; no new bank-side API surface).
- [ ] `mvn install` passes.

## Blocked by

`.scratch/send-and-receive-money/issues/06-fps-inbound-admin-endpoint-processor-unknown-beneficiary.md`
