# 06 — Stub counterparty migration

Status: ready-for-agent

## Parent

[Real SQS messaging PRD](../PRD.md)

## What to build

Migrate `malai-stub` (the scheme counterparty) to the new idiom so the full stack exchanges real messages. The stub's FPS and SWIFT schemes become `@SqsListener` consumers of the command queues and `SqsTemplate` producers of the event queues, using the shared catalog names. The inbound emitter and the ambient (payroll) activity scheduler migrate to `SqsTemplate`, with the scheduler retaining `@Scheduled` + `@SchedulerLock`. Remove the stub's direct `software.amazon.awssdk:sqs` dependency. Stub acceptance tests exercise consume-command / produce-event over real Localstack queues.

## Acceptance criteria

- [x] The stub consumes submit/return commands via `@SqsListener` and produces settled/returned/received events via `SqsTemplate`, using the shared catalog queue names.
  - `StubFpsScheme` `@SqsListener`s `payments-fps-command-submit.fifo` (decide → settle/return) and `payments-fps-command-return.fifo` (records the return-to-sender), producing `payments-fps-event-settled.fifo` / `payments-fps-event-returned.fifo` via `SqsTemplate`. `StubSwiftScheme` `@SqsListener`s `payments-swift-command-submit.fifo` → `payments-swift-event-settled.fifo`. All names derived from `PaymentsQueues` via `QueueNaming.physicalName(prefix, …)`.
- [x] The inbound emitter and ambient payroll scheduler publish inbound received events over `SqsTemplate`; the scheduler retains `@Scheduled`.
  - `StubInboundEmitter` produces `payments-fps-event-received.fifo` / `payments-bacs-event-received.fifo` via `SqsTemplate`. `StubAmbientActivityScheduler` keeps `@Scheduled(cron=…)`. **`@SchedulerLock` deliberately not added** — see note below.
- [x] The stub no longer depends on `software.amazon.awssdk:sqs` directly.
  - Removed from `malai-stub/pom.xml`; the stub now uses `SqsTemplate` / `@SqsListener` from `spring-cloud-aws-starter-sqs` (transitive via `malai-commons`). No `software.amazon.awssdk.*` import remains in the stub.
- [x] Stub acceptance tests run against real Localstack queues.
  - `StubSchemeAcceptanceTest` (7 tests) boots the stub against Localstack with a per-JVM `queue-name-prefix`, app-provisioned queues, and plays the bank: produces commands / posts the demo endpoints, captures events off the event queues with `@SqsListener`, polls with Awaitility. `StubCopControllerTest` also points at Localstack (booting the stub now provisions queues + starts listeners).
- [x] `mvn install` is green.
  - BUILD SUCCESS across all 31 modules (~8.5 min); `malai-stub` 37 tests green over real Localstack; `malai-api` builds.

## Blocked by

- 01 — Foundation + FPS happy-path round-trip on real SQS

## Notes for next iteration (issue 07)

- **`@SchedulerLock` on the payroll cron was intentionally NOT added.** The stub has no datasource (its pom has no JPA/Postgres), so it cannot construct the `JdbcTemplateLockProvider` that `@EnableSchedulerLock` requires; ShedLock only matters for the horizontally-scaled `malai-api`, and the `everyScheduledMethodHasSchedulerLock` ArchUnit rule is not applied to the stub (no architecture test in the module). The scheduler keeps `@Scheduled`. If a future change scales the stub out or gives it a datasource, revisit.
- The stub still references the new `MalaiSqsMessagingProperties` (region / endpoint-override / credentials / `queue-name-prefix`). The old awssdk `MalaiSqsConfiguration` + `MalaiSqsConfigurationProperties` in `malai-commons` are now fully unused (nothing in the stub or the bank imports them) and are issue 07's to delete.
- `malai-stub/application.yaml` was trimmed to the new shape (dropped the per-queue-URL and poll-delay props, added `queue-name-prefix`). The broader e2e/docker `MAL_SQS_*_QUEUE_URL` env vars + `localstack-init-queues.sh` remain for issue 07.
- The failing-listener visibility-backoff still has no dedicated red-green test on any path (carried from issues 01–05); the stub's submit listener throwing once would be another natural place to cover it.
