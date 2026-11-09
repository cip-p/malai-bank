# 03 — Acceptance-test JVMs hand the database back at post-migration state

Status: done
Category: bug

## What to build

Left open by issue 01 and restated in the `02` commit notes: a full `mvn install` *ends* with
`malai_test` at 0 users / 0 accounts / 0 ledger_accounts. `malai-seeder` restores post-migration
state, then the acceptance-test modules after it truncate through `deleteAllFixtures` and do not,
dropping the V14 seed — the system clearing user, three suspense accounts, and the three
`ledger_accounts` registry rows.

Issue 01 fixed the seeder tests with "the polluter restores". The acceptance-test JVMs are the
other polluter. Make them hand the database back the same way, so the invariant holds whichever
module happens to run last — and so a scoped `mvn test -pl <module>` leaves the database fit for
whatever a developer runs next.

## Acceptance criteria

- [x] A JVM that truncated through `MalaiAcceptanceTest` leaves `malai_test` at post-migration
      state, not empty
- [x] A JVM that did not truncate leaves the database untouched — the e2e suite points at a
      different database and must not be reset
- [x] The hand-back reaches every module without a per-module opt-in, and cannot fall behind a
      new migration
- [x] Tests still start from empty fixture tables; no existing test's arrangement changes
- [x] `mvn install` green, and ends with `malai_test` at post-migration state

## Blocked by

None.

## Comments

### Resolution (2026-07-26)

Reproduced from the state the `02` build left behind: `malai_test` sat at 0 users / 0 accounts /
0 ledger_accounts, where post-migration state is 1 / 3 / 3 / 1.

`SharedDatabaseHandBack` records that a JVM borrowed the shared database — set from
`MalaiAcceptanceTest.cleanTables()`, the one place acceptance tests truncate — and
`SharedDatabaseHandBackListener`, a JUnit Platform `TestExecutionListener` registered through
`META-INF/services`, calls `MalaiSchema.resetToMigratedState()` when that JVM's test plan
finishes.

Decisions:

- **At plan end, not per class.** Restoring inside `deleteAllFixtures` would put the V14 rows in
  front of every test's arrangement — ~200 classes would pay a Flyway reset, and the two
  whole-table `theLedgerAccountsInTheDatabase()` assertions would have to absorb three rows they
  never asked for. Tests still start from empty fixture tables; only the JVM's exit changes.
- **Registration, not a per-module hook**, for the reason issue 02 chose an
  `EnvironmentPostProcessor` over a line in each `application-test.properties`: a per-module list
  drifts and a new acceptance-test module can forget it. Every module that truncates already has
  `malai-acceptance-test-commons` on its test classpath.
- **Only a borrower hands back.** `MalaiE2ETest` is a separate base class calling
  `deleteAllFixtures` against its own database on port 5433, so it never records a borrowing —
  and `resetToMigratedState`'s `malai_test`-only guard would refuse it anyway.
- **Flyway, not a hand-written re-insert**, per issue 01: the restore has to reproduce
  post-migration state, and a hand-maintained list of seeded rows falls behind the next migration
  that seeds one.

Mutation-checked three ways: dropping the borrowed guard turns the "leaves the database alone"
test red; unwiring `recordBorrowing()` from `cleanTables()` turns both hand-back tests red; and
the discovery test was observed red before `META-INF/services` existed.

Checked and cleared on the way: `malai-sql` puts `flyway-core` on ~20 test classpaths, which
could have woken Spring Boot's Flyway auto-configuration in test contexts. It did not — every one
of the 31 `FlywayExecutor` runs in the full build is attributable to a hand-back, to `malai-sql`'s
own `MalaiSchemaTest`, or to the two seeder modules that already reset deliberately.

Verified: `mvn install` green — 30:43, 3532 tests, zero failures across all 58 modules — and the
database ends at 1 user / 3 accounts / 3 ledger_accounts / 1 account holder. 23 hand-backs fired,
one per acceptance-test JVM.

**Cost, for whoever cares later:** a hand-back is a Flyway clean + migrate, measured between 2s
and 6s; 23 of them is roughly 1.5–2 minutes. The full build went 27:12 → 30:43, so about half the
increase is this and about half is run-to-run variance on a machine also hosting the e2e stack.
If it ever matters, the lever is restoring only the seeded rows instead of a full clean + migrate,
at the cost of the drift-proofness issue 01 bought with Flyway.
