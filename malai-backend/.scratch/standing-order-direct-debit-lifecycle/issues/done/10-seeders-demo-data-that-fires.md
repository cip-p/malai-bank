# 10 — Seeders: demo data that actually fires

Status: done

## Parent

`.scratch/standing-order-direct-debit-lifecycle/PRD.md`

## What to build

Align both seeders with the live lifecycle so demo accounts feel alive from the first minute. The canonical demo seeder populates the new lifecycle columns and derives each instrument's next date from the anchor rhythm its own backdated history establishes — if the seeded feed shows the energy company collecting on the 15th, the next collection is the next 15th, not an arbitrary offset — and links its standing orders to its seeded payees, making the payee-deletion block demoable. The registration seeder additionally gives every fresh self-registered user a few payees, linked standing orders and mandates with the earliest next dates one to three days out, so the register-and-explore persona finds populated pages immediately and scheduled activity lands while they're still evaluating.

## Acceptance criteria

- [x] Canonical seed: every standing order and mandate carries status ACTIVE and a start-date anchor; next payment and collection dates continue the rhythm visible in the seeded transaction history
- [x] Seeded standing orders link to seeded payees and pay matching snapshots
- [x] Seeder determinism preserved: repeated resets produce byte-for-byte identical output, existing determinism test extended to the new fields
- [x] Registration seeding: fresh user receives a few payees, linked ACTIVE standing orders and fixed-amount mandates with plausible UK creditors; earliest next dates one to three days out; existing idempotency guard (no-op when data exists) intact
- [ ] Reset environment verifies end to end: seeded instruments fire on schedule through the executors without manual intervention

## Progress

2026-07-08 — both seeder slices landed on `main`. Completes the seeder half of the
SO/DD lifecycle PRD.

**Canonical seeder (`malai-seeder`).** Sarah's 3 payees, 3 direct debits and 3 standing
orders now come from a new `com.malai.seeder.data.InstrumentsSeed` (extracted from
`SeedCommand` so identifiers, payee linkage and the date derivation are unit-testable
without booting Spring — mirrors `TransactionsSeed`).
- **Rhythm-derived next dates.** Each instrument carries an `anchorDaysAgo` matching where
  its narrative lands in the backdated feed; `start_date = today − anchorDaysAgo` (a real
  past anchor) and the next payment/collection date is the first on-grid occurrence
  strictly after today via the shared `com.malai.commons.schedule.OccurrenceSchedule` the
  live executors use — so it continues the rhythm (the energy DD's next collection is the
  next same-day-of-month) instead of the previous arbitrary +5/+7-day offsets. Dates are
  future, so Sarah's pinned opening balances (£1247.83 / £8400.00) are preserved: the
  instruments fire on their own schedule, not on the first executor tick.
- **SO → payee linkage.** The two external standing orders (Rent → Jamie Roberts, Commute
  top-up → TfL Top-up) now set `payee_id` to the seeded payee and pay that payee's exact
  name / sort code / account number snapshot — the payee-deletion block (issue 04) is now
  demoable against the canonical dataset. The savings-sweep SO stays `payee_id = null` by
  design (a transfer to Sarah's own SAVINGS account has no address-book payee).
- Every SO/DD is `InstrumentStatus.ACTIVE` with its `start_date` anchor set. `SeederDeterminismTest`
  extended (+6 cases): stable/unique identifiers, payee-linked snapshot match, the internal
  sweep resolves its own snapshot, DDs are fixed-amount, and the anchor derivation is
  future + deterministic. "Byte-for-byte identical" holds to the same standard the seeder
  always had — over the deterministic structure (UUIDs, amounts, refs, frequencies,
  statuses, links, relative offsets); absolute timestamps track `timeService.now()` as
  before, since the seeder has never stubbed time.

**Registration seeder (`malai-registration-seeder`).** Every fresh self-registered user now
also gets 3 payees, 2 payee-linked ACTIVE standing orders and 3 ACTIVE fixed-amount
mandates (`RegistrationInstrumentsSeed` templates, all reusing `ClearingAccountsSeed`
entries so no new literal sort code enters the build — the sort-code lint stays green with
no new exclusion).
- **Raw-SQL, not service configs.** New `InstrumentSeedHelper` writes `payees` /
  `standing_orders` / `direct_debits` + a matching `SEED_*_CREATED` audit row via
  `JdbcTemplate`, exactly like `LedgerSeedHelper`. Importing the DD/SO service
  configurations instead would component-scan their whole packages and pull the per-minute
  executor/requester schedulers + scheme clients (and their base-url/cognito/cron props)
  into every host that seeds a user — avoided entirely. Payees are inserted before the
  standing-order FK resolves in the same `@Transactional` commit.
- **Fires soon.** `start_date = today + startOffsetDays` (1–3 days out; earliest exactly 1),
  `next_*_at = start_date` midnight — so scheduled activity lands while the register-and-explore
  persona is still evaluating.
- **Idempotency intact.** Instruments are written after the existing "≥1 ledger entry →
  Noop" guard, so a second `seedFor` writes nothing (verified: payee/SO/DD counts unchanged
  on the no-op path). The audit-chain-advance assertion updated to `transactionCount +
  AUDIT_EVENT_COUNT`; `AuditChainVerifier.verify()` stays Ok.

Verification: full reactor `mvn install` BUILD SUCCESS. `malai-seeder` green (17:
`SeederDeterminismTest` 12 + `SeedUserCommandTest` 5); `malai-registration-seeder` green
(6, incl. the extended seeded/no-op/audit-chain cases against Postgres);
`MalaiSortCodePrefixLintTest` green; `malai-api` registration-seeder acceptance green (5:
auto-seed on verify-code + `/seed-demo-data` happy/idempotent/sad through the real HTTP
endpoint and full aggregator — confirms the new `InstrumentSeedHelper` bean wires and the
payee unique constraint is protected by the idempotency guard).

Remaining (deferred, environmental — same limitation as issues 06/07/08/09's last AC): the
literal "reset environment verifies end to end" clause (reset the demo, watch the seeded
instruments fire on the per-minute executors and land in the feed) needs the full running
stack + localstack + Cognito stub + AWS creds, not reproducible here. The rows are written
exactly as the live executors' due-scans require (`status='ACTIVE'`, non-null anchor,
`next_*_at <= now` once the date arrives, debtor SORT_CODE identifier present), and issues
06/08 verify the executor/requester clocks under stubbed time. Issue kept open with this
note.

## Blocked by

- 01 — cancel a standing order (schema) — done
- 02 — cancel a direct debit (schema) — done
- Best verified after 06 and 08 are deployed, but only the schema slices block starting
