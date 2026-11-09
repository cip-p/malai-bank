# 03 — Every registered user gets a populated, warning-tripping forecast

Status: done

## Parent

`.scratch/balance-forecast/PRD.md`

## What to build

Make the forecast worth looking at the moment a fresh user explores it. Today the registration seeder anchors instruments with future next-dates, so a new user's forecast would show only Expected payments and an empty Completed section, and would not dip into the red. Extend the registration seeder so **every registered user's** primary account carries a spread of active standing orders and direct debits that produces a full timeline and trips the warning.

The seeded arrangement must place some occurrences earlier this month (populating **Completed occurrence**s), some later this month (populating **Expected occurrence**s), and size the totals so `forecastBalance` goes negative — the "you look fine now, but you won't be by month-end" moment. Because Completed occurrences are schedule-projected at nominal amounts, the seeder also writes **matching backdated ledger postings** for the completed occurrences so the account's available balance genuinely reflects them and the transactions feed shows them too — no seam for a demo audience to spot. Day-of-month is anchored relative to real now (as the seeder already does with offsets), accepting the month-boundary caveat the existing SO/DD e2e tests already live with.

## Acceptance criteria

- [x] Registration seeder places, on each fresh user's primary account, active standing orders and direct debits whose occurrences straddle today — at least one clearly completed and at least one clearly expected this month
- [x] Seeded amounts are sized so the resulting `forecastBalance` is negative (warning state), given the seeded starting balance
- [x] For each seeded completed occurrence, the seeder writes a matching backdated ledger posting so available balance reflects it and it appears in the transactions feed; the completed rows and the balance are internally consistent
- [x] Standing orders and direct debits are both represented, so both `STANDING_ORDER` and `DIRECT_DEBIT` tags appear in the seeded forecast
- [~] Verified end to end: a freshly-registered user's `GET /accounts/{accountUid}/forecast` returns non-empty Completed and Expected lists and a negative `forecastBalance` — test authored + compiles; NOT executed live (see caveat below)
- [x] Seeder change does not regress existing registration-seeded demo state (accounts, existing instruments, spend) — existing seeder tests stay green
- [x] The canonical Sarah Mitchell seed is left unchanged (the forecast is showcased via the registration fresh-user path, per the PRD)

## Blocked by

- `.scratch/balance-forecast/issues/01-forecast-from-standing-orders.md`
- `.scratch/balance-forecast/issues/02-include-direct-debit-mandates.md`

## Completed

The warning-tripping forecast is seeded **only on the random registration path**
(`RegistrationSeederService.seedFor(email)`), branched off the deterministic
canonical path (`seedFor(email, seed)`) so the pinned Sarah Mitchell dataset is
byte-unchanged — the forecast is showcased via the fresh-registration persona, per
the PRD. New `ForecastSeed` templates: two occurrences already completed earlier
this month (a Commute standing order to TfL + an Octopus Energy DD, anchored on/
before today, clamped to the 1st near the month start), each with a matching
backdated `PAYMENT_SETTLEMENT` / `DIRECT_DEBIT_COLLECTION` ledger posting so the
available balance drops and the transactions feed shows them; and two still
expected before month-end (a Spotify DD + a Rent standing order to Eve Robinson).
The Rent amount is computed at seed time = `availableBalance + OVERSHOOT (£250) −
fixed-expected`, so the projected month-end balance lands exactly `−£250` in the
red regardless of the (unchanged, random £500–£15,000) opening balance. All
instruments MONTHLY (one occurrence each); every counterparty reuses an existing
`ClearingAccountsSeed` entry (no new sort code).

Verified (serial `mvn`, Postgres up; the task's `npm run test`/`typecheck` are N/A
in this Maven/Kotlin repo): registration-seeder integration 10/0/0 (incl. the new
`seeds a warning-tripping forecast` case proving straddle, both types, 2 backdated
completed postings, `available == opening − completed`, and `available −
expectedOutflow == −OVERSHOOT`), malai-seeder SeedUserCommandTest 5/0/0, malai-api
RegistersWithPopulatedTransactionsTest 1/0/0 + SeedDemoDataControllerTest 2/0/0
(happy) + 2/0/0 (sad) — ledger-count bounds widened by the 2 completed postings.
Deterministic (Sarah) integration cases stay green, confirming the canonical seed
is unchanged.

**Caveat (same as slices 01/02):** the e2e
`InsightsTest.forecastForAFreshlyRegisteredUserStraddlesTodayAndDipsIntoTheRed`
drives the real register → verify-code flow (the only path that fires the seeder
hook — `.thatIsRegistered()` skips verify-code and does NOT seed) → get-accounts →
get-forecast, and asserts clock-robust invariants (non-empty Completed + Expected,
`forecastBalance == available + expectedTotal`, `forecastBalance < 0`, both types,
the four fan-out arrows). It **test-compiles** but was NOT executed against a live
stack (e2e gated behind `e2e.skip=true`; the running `malai-e2e-*` containers
predate this change). Rebuild the stack
(`malai-end-to-end-tests/start-e2e-environment.sh`) and run with
`-De2e.skip=false` to exercise it. Month-boundary caveat (accepted, per the issue):
on the final calendar day of a month the Expected list is empty and the forecast
is non-negative — there is no room after today for an expected occurrence.
