# Stub scheme outcome wiring (OutboundOutcomeResolver + FPS/BACS handlers consult the directory per submission)

Status: done

## Note on BACS scope

When the issue was written it referenced `StubBacsScheme` (or whichever
BACS submit handler existed). Verified during implementation: **no BACS
outbound path exists yet** — `bacs-gateway` is inbound-only, `malai-stub`
only has `StubBacsInboundNotifier` (no scheme submit handler), and
`payments-service.PaymentsService` only dispatches `Rail.FPS` and
`Rail.SWIFT` (BACS_DIRECT_CREDIT exists on the entity but is set only on
inbound persistence). Wiring the resolver into a non-existent path was
out of scope; the BACS half of this issue is N/A until an outbound BACS
submission flow lands.

The FPS half is fully wired: `OutboundOutcomeResolver` is the canonical
lookup, `StubFpsScheme` consults it on every submit, and `STUCK_PENDING`
short-circuits to ACK-only.

## Parent

[`.scratch/sandbox/PRD.md`](../PRD.md)

## What to build

Wire the new `outboundOutcome` field (landed in issue 01) into the FPS and BACS scheme handlers so that outbound payments to fictional **Counterparties** produce the deterministic outcome the catalogue advertises. This is the slice that makes [ADR-0017](../../../../malai-webapp-v1/docs/adr/0017-sandbox-counterparty-outcome-destination-deterministic.md) (webapp-side, captures the design decision) real on the stub side. After this slice, the brief's "an account for rejected payment, one for in progress, etc." asks behave correctly in the existing **Transfer** flow — independent of any Sandbox UI work.

End-to-end behaviour:
- A new pure-Kotlin deep module `OutboundOutcomeResolver` exposes `resolve(sortCode, accountNumber): OutboundOutcome`. Known **Counterparties** return their configured outcome; unknown destinations return `SETTLES` (per [ADR-0017](../../../../malai-webapp-v1/docs/adr/0017-sandbox-counterparty-outcome-destination-deterministic.md) — failure modes are concentrated in named fixtures).
- The stub's FPS scheme handler consults the resolver when processing an outbound submit. `SETTLES` → emit settlement on the existing scheme/SQS path. `RETURNS` → emit return-to-sender with the row's `returnReason`. `STUCK_PENDING` → ACK the submit and emit nothing further (no settlement, no return, no timeout).
- The stub's BACS scheme handler does the same.
- The legacy `POST /demo/arm-return` global state path is **untouched**. The new resolver-driven path is the deterministic-by-destination way; `armReturn` continues to work for backwards compatibility with whatever tests / scripts consume it. Where the two could conflict (a destination has a directory entry *and* an arm is active), the existing test contract for `armReturn` decides — i.e. the implementation order should keep `armReturn` behaviour unchanged for the call sites that use it today.
- No webapp / API contract changes in this slice. Demoability is via the existing Transfer flow.

This slice can run in parallel with backend issue 02 (different code paths; both depend on issue 01's schema widening).

## Acceptance criteria

- [ ] New pure-Kotlin class `OutboundOutcomeResolver` in `malai-stub` with a single method `resolve(sortCode: String, accountNumber: String): OutboundOutcome`. Returns the directory row's `outboundOutcome` if present; `SETTLES` otherwise.
- [ ] `StubFpsScheme` (or the equivalent FPS submit handler — verify exact name during exploration) consults `OutboundOutcomeResolver` per outbound submit. Behaviour by outcome:
  - `SETTLES` → existing happy-path settlement is emitted unchanged.
  - `RETURNS` → return-to-sender is emitted on the existing return path with the row's `returnReason`.
  - `STUCK_PENDING` → ACK the submit; no settlement, no return, no further scheme messages are emitted for this payment. The PENDING state persists indefinitely.
- [ ] `StubBacsScheme` (or the equivalent BACS submit handler) does the same.
- [ ] `OutboundOutcomeResolver` unit tests cover: known destination → matching enum; unknown destination → `SETTLES`. No Spring context.
- [ ] Stub FPS scheme integration test: an outbound to a `RETURNS` Counterparty results in a return-to-sender on the existing assertion path with the expected reason. An outbound to a `STUCK_PENDING` Counterparty results in an ACK and no follow-up scheme message (assert no settlement and no return emitted for the duration of the test).
- [ ] Stub BACS scheme integration test mirrors the FPS one.
- [ ] Existing FPS / BACS scheme tests (`StubFpsSchemeTest`, `StubBacsScheme*Test`) continue to pass — outcome behaviour for destinations that are NOT in the directory (`SETTLES` default) preserves the prior contract.
- [ ] Existing `armReturn` tests (`StubFpsDemoControllerTest`) continue to pass — the legacy arm-style path is not regressed.
- [ ] Existing CoP tests continue to pass.
- [ ] `mvn install` from the repo root passes end-to-end.

## Blocked by

- [`.scratch/sandbox/issues/01-catalogue-get-end-to-end.md`](./01-catalogue-get-end-to-end.md) — needs `outboundOutcome` to exist on `StubAccount` and the fixtures to carry it.
