# 01 — Foundation + FPS happy-path round-trip on real SQS

Status: ready-for-agent

## Parent

[Real SQS messaging PRD](../PRD.md)

## What to build

Establish the real-SQS messaging skeleton and prove it end-to-end on the **FPS** outbound submit→settled happy path. This is the walking skeleton every later slice builds on, so it is a human-review gate: the harness, capturer, and provisioner patterns introduced here are copied by slices 02–07.

Introduce, as the single source of truth, a **queue catalog** in `malai-payments-messages` declaring the eight queues under the `<domain>-<component>-<type>-<event>.fifo` convention (the dotted scheme in `docs/queues-naming-conventions.md` with dots→hyphens). Add a domain-agnostic **provisioner** in `malai-commons` that, on application startup, create-if-absent's every queue plus a paired `…-dlq.fifo` with a uniform `maxReceiveCount=3` redrive policy — idempotent and safe to run from any deployable. Add the `spring-cloud-aws` `SqsTemplate` + listener-container configuration, including the failure path that extends message visibility by a configurable flat backoff before rethrowing. Add a `queue-name-prefix` setting (empty by default, set per test JVM).

Build the acceptance-test harness: add Localstack to the local startup script; add an SQS interaction capturer modelled on the existing WireMock capturer, with an `SqsClassifier` that maps a queue name to a scheme party (FPS to begin with) and a human description; add an `eventually {}` Awaitility helper in the acceptance-test commons.

Migrate the FPS path: a concrete FPS producer sends the submit **command** via `SqsTemplate` (FIFO group = paymentId, explicit dedup id); an `@SqsListener` consumes the settled **event** and delegates to the existing settled consumer, with the dedup check performed inside the listener transaction. Delete `InMemoryFpsScheme` and the `FpsScheme` interface and rework the FPS submit/settle test helpers to capture the real command and produce the real event. Restore a green build on the already-swapped `spring-cloud-aws` dependency.

## Acceptance criteria

- [x] On startup the app create-if-absent's all eight queues and a paired `…-dlq.fifo` per queue with the `maxReceiveCount=3` redrive policy attached; re-running startup is a no-op. (`QueueProvisioner` in `malai-commons`, run from `SmartInitializingSingleton` before listener containers start.)
- [x] Queue names follow `<domain>-<component>-<type>-<event>.fifo`, come from a single catalog, and are prefixed by a `queue-name-prefix` that defaults to empty. (`PaymentsQueues` + `PaymentsQueueCatalog` in `malai-payments-messages`, `QueueNaming` + `MalaiSqsMessagingProperties.queueNamePrefix` in `malai-commons`.)
- [x] An authenticated FPS outbound payment publishes a submit command to the FPS command queue via `SqsTemplate` (FIFO group = paymentId, explicit dedup id), synchronously. (`FpsCommandProducer`.)
- [x] A settled event on the FPS settled queue is consumed by an `@SqsListener` and transitions the payment PENDING→SETTLED; the `sqs_message_dedup` check runs inside the listener transaction. (`FpsOutboundSettledListener` → existing `FpsOutboundSettledConsumer` `@Transactional`.)
- [x] A failing listener extends the message's visibility by the configured backoff before the exception propagates. (`MalaiSqsMessagingConfiguration` listener-factory `ErrorHandler` calls `Visibility.changeTo(backoffSeconds)` then rethrows. **Wired but not yet covered by a dedicated failing-listener test** — carried to a follow-up; the consumer-transaction/dedup path is covered by `duplicateSettledMessageIsDeduplicated…`.)
- [x] A payments acceptance test runs against real Localstack queues, isolated by a per-JVM prefix and unique per-test IDs (no `PurgeQueue`), asserts the captured submit command, produces the settled event, and polls the SETTLED lifecycle with `eventually {}`. (`FpsOutboundPaymentsControllerTest` × 3, `CapturedFpsCommands` + `FpsCommandCaptureListener` + reworked `WithPayments`.)
- [x] The FPS submit and settled interactions render on the Kensa sequence diagram in the FPS scheme lane, classified by queue name. (`SqsClassifier` + `SqsInteractionCapturer` + `SqsInteractionRenderer`, `MalaiParty.Fps/Swift/Bacs`.)
- [x] `InMemoryFpsScheme` and the `FpsScheme` interface are deleted; no `malai.sqs.enabled` guard remains on the FPS path. (Also deleted `SqsFpsScheme`, `FpsOutboundSettledPoller`.)
- [x] `mvn install` is green.

## Progress (landed 2026-05-25)

Foundation slice landed end-to-end on FPS submit→settled over real Localstack queues. Key decisions / fixes made while landing:

- **spring-cloud-aws version corrected `4.0.2` → `3.2.1`.** The WIP pom pinned `4.0.2`, which does not exist for Spring Boot 3.5.x (4.0.x targets Boot 4). 3.2.1 is the resolvable, Boot-3-compatible line; its `SqsTemplate` / `@SqsListener` / `Visibility` / `ErrorHandler` API is what the new config targets.
- **AWS SDK BOM import added (before the spring-cloud-aws BOM).** Pulling in `spring-cloud-aws-starter-sqs` activated the spring-cloud-aws dependency BOM, which down-managed the transitive AWS SDK artifacts and split the classpath against our explicit `cognitoidentityprovider:2.42.25` (→ `NoClassDefFoundError: SdkClientJsonProtocolAdvancedOption` whenever a context built a `CognitoIdentityProviderClient`). Importing `software.amazon.awssdk:bom:2.42.25` first pins every AWS SDK artifact consistently.
- **Always-on `SqsAsyncClient` is the cross-cutting keystone.** Because `malai-commons` is component-scanned by every service, an always-on, region-configured `SqsAsyncClient` bean (built from `malai.sqs.*`) makes spring-cloud-aws's auto-config back off and prevents every non-payments service from failing at startup on AWS region resolution. The `QueueProvisioner` only acts where a `QueueCatalog` bean is present (payments-service / malai-api), so idle services create an unused client and never connect.
- **Transitional coexistence (scope = FPS only).** SWIFT / BACS / FPS-inbound / FPS-returned still use their existing `InMemory*` producers + direct-invocation test helpers and the `@ConditionalOnProperty(malai.sqs.enabled)` awssdk classes (now never active in tests). Issues 02–06 migrate them; issue 07 deletes the old `MalaiSqsConfiguration` + per-queue-URL props + the `malai.sqs.enabled` flag everywhere.
- **Test isolation by short per-JVM prefix.** Per-JVM prefix (`p<8hex>-` / `a<8hex>-`) + unique per-test IDs; no `PurgeQueue`. Prefix kept short because SQS enforces an 80-char name limit *including* the `.fifo` suffix, and `<prefix>payments-fps-command-submit-dlq.fifo` blew past it with a UUID-length prefix.
- **malai-api + payments-service acceptance tests now point at Localstack** (`MAL_SQS_ENDPOINT_OVERRIDE` or `http://localhost:4566`) because the bundled FPS `@SqsListener` + provisioner need a broker at boot. `mvn install` therefore requires Localstack up (added to `start-docker-containers-local.sh`); e2e remains skipped (`e2e.skip=true`).

## Notes for next iteration

- **Failing-listener visibility-backoff test** is the one AC behaviour implemented-but-not-red-green-tested. Add a payments acceptance test that produces a settled event whose handler throws once, and assert redelivery (or assert via the DLQ after `maxReceiveCount`). Needs a way to make the consumer fail deterministically.
- Issues 02–06 still own SWIFT / BACS / FPS-inbound / FPS-returned / stub migration to `SqsTemplate` + `@SqsListener`; the awssdk `MalaiSqsConfiguration`, the `Sqs*`/`InMemory*` non-FPS schemes, the pollers, and the `malai.sqs.enabled` flag survive transitionally.
- Issue 07 owns the e2e cleanup (`localstack-init-queues.sh`, the docker-compose `ready.d` hook, `MAL_SQS_*_QUEUE_URL` env vars) and removing the awssdk config + per-queue-URL props. The WIP already removed `MAL_SQS_ENABLED` from `docker-compose.e2e.yml` and trimmed `localstack-init-queues.sh`; full e2e revalidation is deferred to 07.
- The pre-existing duplicate `jackson-module-kotlin` entry in root `<dependencyManagement>` still emits a build WARNING (unrelated to this slice; left untouched).

## Blocked by

- None - can start immediately.
