# 01 — Seeder tests hand the test database back at post-migration state

Status: done
Category: bug

## What to build

Flagged in the `aml 09` commit notes: `AccountIdentifierRepositoryTest` collides on
`(SORT_CODE, 12345678)` if the seeder tests ran against `malai_test` beforehand, and clears
only after a manual `./malai-db.sh`. Root cause is that the seeder integration tests truncate
`@BeforeEach` but never after, so they leave a full demo estate behind for whatever runs next.
`mvn install` is therefore not re-runnable without a manual database wipe.

Make the seeder tests borrow the shared database and hand it back as they found it.

## Acceptance criteria

- [x] The collision reproduces deterministically before the fix, and is green after
- [x] Seeder tests leave `malai_test` at post-migration state, not at an estate and not empty
- [x] The restore cannot drift as migrations are added
- [x] The restore refuses to run against any database but `malai_test`
- [x] `mvn install` green

## Blocked by

None.

## Comments

### Resolution (2026-07-26)

Reproduced first: `mvn test -pl malai-seeder -Dtest=SeedCommandIntegrationTest` leaves 12 users,
12 accounts, 243 audit events and `SORT_CODE/12345678`; `AccountIdentifierRepositoryTest` then
fails with `account_identifiers_type_value_unique`. Confirmed cross-run, not intra-run —
`malai-seeder` sits at reactor position 54, after `account-service` at 35.

Two independent defences, because either alone leaves a hole:

- **The polluter restores.** `MalaiSchema.resetToMigratedState()` (new, `malai-sql`) runs Flyway
  clean + migrate, called `@AfterAll` from the three test classes that write committed data:
  `SeedCommandIntegrationTest`, `VerifyChainCommandIntegrationTest`,
  `RegistrationSeederServiceIntegrationTest`.
- **The victim stops depending on a demo value.** `AccountIdentifierRepositoryTest` used
  `12345678` — Sarah's seeded current-account number. Now `99000002`, matching the `99000001`
  its neighbours already use, so it survives a hand-seeded database too. Verified by planting
  the colliding row and re-running.

Flyway rather than a hand-written truncate-and-reseed because the restore has to reproduce
*post-migration* state, not an empty one: V14 seeds three suspense accounts, the system user and
three `ledger_accounts` registry rows. The existing hand-rolled `seedSchemeSettlement()` helper
in the seeder tests is already unfaithful to V14 — one suspense account, no ledger registrations —
which is exactly the drift a catalog- or list-driven reset would keep accruing. Flyway is the
only definition of that state that cannot fall behind a new migration.

The reset is guarded to refuse any database whose name is not `malai_test`, and refuses before
connecting. The guard is not theoretical: the RED test for it, written before the guard existed,
wiped `malai_local`. The test now targets a fictional database name so it can never touch a real
one.

`MalaiDbMigrator` was refactored onto the same `MalaiSchema.flyway(...)` builder, so the two
Flyway placeholders V6 needs are declared once instead of twice. `./malai-db.sh` re-verified
end to end after the refactor.

**Note for next iteration:** a full `mvn install` still *ends* with an empty-but-migrated
database, because the acceptance-test truncation in the last modules (`malai-api`, `malai-stub`)
wipes and does not restore. That is pre-existing and unchanged here; both DB-sensitive service
modules were re-run from that end state and are green. The second flake in the `aml 09` notes,
`ChainVerificationJobTest`, does **not** share this cause — it passes against a deliberately
polluted database, because `theAuditChainIsAtGenesis()` truncates `audit_events` itself. It
remains unreproduced and unexplained.
