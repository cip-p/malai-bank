# 02 — Disarm the shared-state crons inside test JVMs

Status: done
Category: bug

## What to build

The second flake left unexplained by issue 01: `ChainVerificationJobTest` failed once mid-reactor
and passed in isolation. Issue 01 ruled out database pollution as the cause — the test truncates
`audit_events` itself — and left it unreproduced.

Find the real cause and fix it.

## Acceptance criteria

- [x] The failure reproduces deterministically before the fix
- [x] The mechanism is understood, not papered over
- [x] A test pins the invariant, and that test is RED before the fix
- [x] The fix covers every Spring context in a test JVM, not just the one that flaked
- [x] `mvn install` green

## Blocked by

None.

## Comments

### Resolution (2026-07-26)

**Cause.** `@EnableScheduling` is active in test contexts, and `ChainVerificationJob` is
`@ConditionalOnProperty(matchIfMissing = true)` with its cron defaulting to top-of-the-hour. No
test profile ever silenced it, despite the job's own comment saying to. So a scheduler thread
sits inside every acceptance-test JVM waiting to fire `runVerification()` against `malai_test`.

When it fires, it takes the ShedLock first. The test's own `job.runVerification()` is then
*silently skipped* — `@SchedulerLock` returns without running — and the test reads
`lastResultForScope("global")` from the background run instead of its own. The assertion fails as
`expected:<1L> but was:<0L>`: the scheduler verified the chain in the window after
`theAuditChainIsAtGenesis()` and before the test's audit event was recorded.

That explains the signature exactly. An isolated run is short and rarely straddles `HH:00:00`; a
~28-minute reactor often does, and whichever module's JVM is alive at the tick pays.

Reproduced deterministically by setting `audit.verify.scheduler.cron=*/1 * * * * *` in
auth-service's test properties: `Tests run: 149, Failures: 1`, failing on exactly that assertion.
The same background thread also logged repeated
`CHAIN INTEGRITY FAILURE: scope=global GapAtSeq expected=1 actual=2` — the verifier reading a
chain that another test class was mid-way through truncating. Those are the SOC's alert string,
manufactured by the test harness.

**Fix.** `DisarmSharedStateCronsEnvironmentPostProcessor` (new, `malai-test-commons`), registered
through `META-INF/spring.factories`, adds a lowest-precedence property source setting
`audit.verify.scheduler.cron` and `idempotency.cleanup.cron` to Spring's `-`
(`Scheduled.CRON_DISABLED`). The beans stay present so the direct-invocation tests still work;
only the scheduler registration goes away.

An `EnvironmentPostProcessor` rather than a line in each module's `application-test.properties`
for the reason issue 01 chose Flyway over a hand-written reset: a per-module list drifts. This
applies to every Spring context in a test JVM whatever order it is created in, and a new
acceptance-test module cannot forget it. `malai-test-commons` is `test`-scoped in all 23 modules
that depend on it — verified, including `malai-stub`, which ships as a container — so no
deployable ever sees it. Added *last* so a module that genuinely wants a cron in tests can still
set one.

`IdempotencyCleanupJob`'s cron was hardcoded, so it could not be disarmed at all; it now takes the
same property placeholder its sibling job already had, default unchanged at 04:00. It is the same
defect one cron tick away — `IdempotencyCleanupJobTest` also invokes directly and would also have
its call skipped, just only ever at 04:00, which CI can hit.

The two seeder modules use `@SpringBootTest` and do not depend on `malai-test-commons`, so they
get the two properties in their own `application-test.properties`.

**First attempt was wrong, and the test caught it.** The disarm was initially a `System.setProperty`
in `MalaiAcceptanceTestContext.boot`. `BackgroundSchedulesTest` went green — but the build log
still showed `[scheduling-1]` firing every second. `@SpringTest` classes build their own
`@SpringBootTest` contexts that never call `boot`, and if one loads first its context is created
before the property is set. The acceptance context was disarmed while the JVM still had a live
scheduler. Hence the post-processor, and hence `SpringContextBackgroundSchedulesTest` — without a
`@SpringTest`-context assertion, a regression to the `boot`-only approach passes.

**Pinned by** `BackgroundSchedulesTest` (acceptance context) and
`SpringContextBackgroundSchedulesTest` (`@SpringTest` context), which read
`ScheduledAnnotationBeanPostProcessor.scheduledTasks` and assert no task targets either job. Both
RED before the fix, naming `ChainVerificationJob.runVerification` and
`IdempotencyCleanupJob.runCleanup`. Note the runnable is wrapped in `Task$OutcomeTrackingRunnable`,
so a `filterIsInstance<ScheduledMethodRunnable>` version of this assertion passes vacuously — the
first draft did exactly that. It matches on `toString()` instead.

**Verified.** Full `mvn install` green, 27:12, zero failures across all 58 modules — and that run
spanned 16:56→17:23, so it crossed the 17:00 tick that previously armed the race. Zero
`ChainVerificationJob` scheduler firings and zero `CHAIN INTEGRITY FAILURE` lines in the whole
build log, against a nonzero count before.

**Note for next iteration:** the end-state issue from issue 01 is unchanged and still open — a
full `mvn install` finishes with `malai_test` at 0 users / 0 accounts / 0 ledger_accounts. The
reactor's last module is `malai-stub` [58/58]; `malai-seeder` [57/58] restores post-migration
state via `resetToMigratedState()`, then `malai-stub`'s acceptance tests truncate through
`deleteAllFixtures` and do not. `deleteAllFixtures` drops the V14 seed (system user, three
suspense accounts, three `ledger_accounts` registry rows). Nothing currently depends on those
rows surviving — tests that need them register their own — so this is hygiene, not a live
failure, and it is untouched here.
