# 04 — Chain verification surface: prove the MDR chain intact on demand, on schedule, and in CI

Status: done

## Parent

`.scratch/mdr/PRD.md` (Model Decision Record)

## What to build

Integrity proof for scope `mdr` on three surfaces: an admin endpoint on the MDR service (mirroring the audit plane's admin verification endpoint), the existing verify-chain CLI generalised to walk every chain-head scope with a non-zero exit on any failure, and the hourly scheduled verification job iterating scopes under its existing lock. Tamper detection is demonstrated, not assumed.

## Acceptance criteria

- [x] `GET /api/v1/admin/mdr/chain-verification` (ADMIN role) returns Ok with rows-verified for an intact chain
- [x] Tampering a stored hash at a chosen seq makes verification report the mismatch naming that seq — acceptance test mirroring the audit tamper harness
- [x] The verify-chain CLI verifies all scopes: exit 0 when both intact, non-zero when either is tampered (CLI-module test)
- [x] The scheduled job iterates every scope under its scheduler lock and logs an integrity failure loudly without throwing, per the audit job's convention
- [x] Scope isolation asserted: tampering the MDR chain leaves audit verification Ok, and vice versa

## Blocked by

- `02-record-model-decision-tracer.md`
