# PRD: Real SQS messaging — spring-cloud-aws, app-provisioned queues, and real-queue tests

Status: ready-for-agent

## Problem Statement

The event-driven communication between `payments-service` and `malai-stub` was built quickly to unblock the send-and-receive-money flows, and it carries scaffolding that now gets in the way:

- **Messaging is faked in tests and toggled in production.** Every outgoing interaction has an `InMemory*` twin (`InMemoryFpsScheme`, `InMemorySwiftScheme`, `InMemoryInboundReturnPublisher`) selected by a `malai.sqs.enabled` flag. Acceptance tests assert against in-memory lists and invoke consumers directly, so the actual queue wiring — serialization, `@SqsListener`, dedup, redelivery, backoff — is never exercised below the cross-stack E2E tier. A reviewing bank cannot trust that "real bank uses real queues" when the tests prove only the in-memory path.
- **Low-level AWS SDK plumbing.** Producers hand-build `SendMessageRequest`s and consumers are hand-rolled `@Scheduled` pollers guarded by ShedLock, against `software.amazon.awssdk:sqs` directly — more code, more ceremony, and a different idiom from the reference `spring-cloud-aws` implementation the team wants to standardise on.
- **Queues are provisioned in three different places** (a Localstack init script, docker-compose env, and implicitly via `MAL_SQS_*_QUEUE_URL` properties), none of which is the application. There is no single source of truth for what queues exist, and the naming is ad-hoc (`fps-outbound-submit.fifo`) with no DLQs attached.
- **No documented naming convention is actually applied.** `docs/queues-naming-conventions.md` defines a `<domain>.<component>.<type>.<event>` scheme, but the live queues don't follow it.

The headline question a buyer asks is *"this looks like a real bank, and the architecture beneath it is one we'd actually want to run."* The messaging layer is the seam where a real bank wires in Pay.UK / SWIFT / BACS. It has to be real, idiomatic, self-describing, and provably tested end-to-end.

## Solution

Replace the hand-rolled SQS layer with an idiomatic `spring-cloud-aws-starter-sqs` implementation, make real queueing the only mode, and prove it with tests that exchange real messages over a real broker.

- **One messaging idiom.** Producers send via `SqsTemplate`; consumers are `@SqsListener` methods. The custom `@Scheduled` pollers and their ShedLock locks are deleted (ShedLock stays for the genuine cron jobs). On handler failure, the listener extends message visibility by a configurable flat backoff before rethrowing, so SQS delays redelivery.
- **A single naming convention, applied.** Queues follow `<domain>-<component>-<type>-<event>.fifo` — the dotted convention from `docs/queues-naming-conventions.md` with dots replaced by hyphens (SQS forbids dots mid-name) and the mandated `.fifo` suffix. `command-*` queues carry instructions the bank issues *to* a **Rail** (bank → **Stub**); `event-*` queues carry notifications the rail sends back (**Stub** → bank).
- **The application owns its queues.** A shared, domain-agnostic provisioner in `malai-commons` reads a payments **queue catalog** held in `malai-payments-messages` and create-if-absent's every queue plus a paired dead-letter queue (`…-dlq.fifo`, uniform `maxReceiveCount=3`) on startup — idempotently, run by both `malai-api` and `malai-stub`, against Localstack locally/E2E and real SQS in AWS. The Localstack init script, the docker-compose init hook, and the per-queue-URL properties are removed. `malai-infra` deliberately does **not** manage these queues in Terraform.
- **No more fakes.** The `FpsScheme` / `SwiftScheme` / `InboundReturnPublisher` interfaces and all `InMemory*` implementations are deleted; each collapses to a single concrete producer. `malai.sqs.enabled` and its `@ConditionalOnProperty` guards are gone — SQS is always on.
- **Tests exchange real messages.** Acceptance tests run against a shared Localstack (added to the local startup script), isolated by a per-JVM queue-name prefix plus unique per-test identifiers. The test plays the counterparty: it produces **events** onto the event queues and consumes **commands** off the command queues, exercising the full round-trip through the bank's real `@SqsListener`s, with Awaitility polling the resulting state. Every queue interaction renders on the Kensa sequence diagram, classified by queue name into per-scheme lanes (**FPS**, **SWIFT**, **BACS**), exactly as WireMock HTTP interactions already do.

The buyer reading the architecture sees real FIFO queues with ISO 20022-shaped payloads, dead-letter queues on every queue, a stub deployable on the other side they could replace with a real scheme integration, and an acceptance-test suite whose sequence diagrams show the bank talking to FPS / SWIFT / BACS over those queues.

## User Stories

### Standardised messaging idiom

1. As a backend engineer, I want producers to send through `SqsTemplate` instead of hand-built `SendMessageRequest`s, so that publishing a scheme message is one idiomatic call that handles serialization and FIFO attributes consistently.
2. As a backend engineer, I want consumers to be `@SqsListener` methods instead of `@Scheduled` pollers, so that the framework owns polling, concurrency, and acknowledgement and I write only the handler.
3. As a backend engineer, I want the `software.amazon.awssdk:sqs` low-level dependency removed from `malai-commons` and `malai-stub` in favour of `spring-cloud-aws-starter-sqs`, so that there is one SQS client idiom across the codebase.
4. As a backend engineer, I want ShedLock removed from the (now-deleted) SQS pollers but retained on the real cron jobs, so that the "every `@Scheduled` method declares `@SchedulerLock`" ArchUnit rule still holds for the jobs that need it.
5. As a backend engineer, I want a failing listener to extend the message's visibility by a configurable flat backoff before the exception propagates, so that SQS waits before redelivery instead of hot-looping, and the affected payment's FIFO message group is held until the failure clears.
6. As a backend engineer, I want outbound sends to be synchronous (block until SQS confirms the enqueue), so that a payment request only reports success once its scheme command is durably queued.

### Naming and queue contract

7. As a backend engineer, I want every queue named `<domain>-<component>-<type>-<event>.fifo`, so that the queue's domain, rail, direction, and meaning are readable from its name alone.
8. As a backend engineer, I want `command-*` queues to mean bank→rail instructions and `event-*` queues to mean rail→bank notifications, so that direction is unambiguous from the queue name.
9. As a backend engineer, I want the eight existing queues renamed onto the convention (submit/return as commands; settled/returned/received as events; across FPS, SWIFT, and BACS), so that the live system matches `docs/queues-naming-conventions.md`.
10. As a backend engineer, I want the inbound-arrival queue called `…-event-received` (not "notify"), so that the resolved domain term **received** is used consistently.
11. As a backend engineer, I want queues to remain FIFO with a per-payment `messageGroupId` and explicit deduplication IDs, so that scheme messages for one payment stay ordered and accidental resends within the dedup window are dropped.

### Application-owned provisioning

12. As an operator, I want the application to create every queue and its dead-letter queue on startup if they don't already exist, so that a fresh environment is self-provisioning with no separate infrastructure step.
13. As an operator, I want each queue to have a paired `…-dlq.fifo` with a `maxReceiveCount=3` redrive policy, so that poison messages stop blocking their payment group after three attempts and land somewhere inspectable.
14. As an operator, I want provisioning to be idempotent and safe to run from both `malai-api` and `malai-stub` concurrently, so that startup ordering never causes a produce-before-create failure.
15. As a backend engineer, I want a single queue catalog as the source of truth for queue names and settings, so that producers, listeners, the provisioner, and the test classifiers all agree without duplication.
16. As a backend engineer, I want a `queue-name-prefix` setting (empty by default), so that the same catalog yields isolated queue sets per test JVM and could separate environments in real AWS.
17. As an operator, I want the Localstack init script, its docker-compose init hook, and the `MAL_SQS_*_QUEUE_URL` environment variables removed, so that there is exactly one mechanism that creates queues.

### Always-on, no fakes

18. As a backend engineer, I want `malai.sqs.enabled` and all its `@ConditionalOnProperty` guards removed, so that there is no in-memory code path to drift out of sync with the real one.
19. As a backend engineer, I want the `FpsScheme` / `SwiftScheme` / `InboundReturnPublisher` interfaces and their `InMemory*` implementations deleted in favour of single concrete producers, so that there is no one-implementation interface ceremony left behind.
20. As a backend engineer, I want the per-queue-URL configuration replaced by catalog-derived names (keeping region, endpoint-override, credentials, and prefix), so that adding a queue means adding a catalog entry, not a property.

### Idempotency and safety

21. As a backend engineer, I want the `sqs_message_dedup` check kept and performed inside the listener's transaction, so that a redelivered message (after a crash between processing and acknowledgement) cannot double-credit a customer on the non-idempotent inbound path.
22. As a backend engineer, I want the dedup `MESSAGE_TYPE` constants aligned to the new queue names, so that the dedup keyspace is consistent with the convention.

### Real-queue testing

23. As a test author, I want acceptance tests to run against a real Localstack started by the local startup script, so that serialization, listeners, dedup, and redelivery are exercised — not bypassed by in-memory stubs.
24. As a test author, I want each test JVM to use its own queue-name prefix and each test to use unique payment/end-to-end IDs, so that the shared Localstack gives reliable isolation without `PurgeQueue` (which FIFO rate-limits to once per 60 seconds).
25. As a test author, I want the test to play the counterparty — produce events onto event queues and consume commands off command queues — so that a payment flows the full round-trip through the bank's real `@SqsListener`s.
26. As a test author, I want an `eventually {}` Awaitility helper in the acceptance-test commons, so that assertions on state that arrives asynchronously poll cleanly within the existing given/when/then style.
27. As a test author, I want every outgoing scheme command asserted by a real capture listener on the command queue, so that "test all outgoing messages" is satisfied against the real wire format.
28. As a test author, I want `WithPayments` reworked to replace the `InMemory*` accessors with real capture listeners and event producers, so that the test vocabulary reflects real queue interactions.

### Diagram and demo storytelling

29. As a developer reading an acceptance test report, I want every queue message rendered on the Kensa sequence diagram, classified by queue name, so that I can see the bank's conversation with the schemes alongside its HTTP calls.
30. As a developer, I want scheme queue interactions to render in per-scheme lanes (FPS, SWIFT, BACS), so that the diagram tells the "looks like a real bank" story of each rail's operator.
31. As a demonstrator, I want the round-trip (submit → settled, or submit → returned) visible as queue interactions in tests and reports, so that the demo narrative of money leaving and confirmation returning is evidenced.

### Stub counterparty

32. As a backend engineer, I want the stub's schemes migrated to `@SqsListener` consumers of command queues and `SqsTemplate` producers of event queues, so that the counterparty uses the same idiom as the bank.
33. As a demonstrator, I want the stub's inbound emitter and ambient activity scheduler to keep working over the new messaging (the payroll cron keeping its `@Scheduled`/`@SchedulerLock`), so that triggered and recurring inbound payments still arrive.

### Regression safety

34. As a backend engineer, I want the cross-stack E2E flows updated to the new queue names and provisioning, so that the full stack still demonstrates send and receive over real SQS.
35. As a backend engineer, I want no change to payment behaviour, ledger postings, suspense topology, Confirmation of Payee, or the audit chain, so that this is a messaging-layer change only and existing payment guarantees are preserved.

## Implementation Decisions

**Queue catalog (deep module, `malai-payments-messages`).** A single declaration of the eight queues, each carrying its logical name, FIFO flag, and message-group key strategy. It derives the physical SQS name (replace `.` with `-`, append `.fifo`), the DLQ name (`…-dlq.fifo`), and applies the configurable `queue-name-prefix`. Pure, no I/O. Lives next to the message DTOs it pairs with so both `malai-api` and `malai-stub` consume it. `malai-commons` stays domain-agnostic and must not depend on it.

**Queue provisioner (deep module, `malai-commons`).** Given the catalog and an SQS client, create-if-absent each queue and its DLQ and attach the redrive policy (`maxReceiveCount=3`). Idempotent; invoked on startup by both deployables. Generic mechanism only — it receives queue definitions, it does not know about payments.

**SQS messaging configuration (`malai-commons`).** Configures `SqsTemplate`, the listener-container factory (object mapper, listener shutdown), the visibility-extension retry backoff, and `X-TestId` message-attribute propagation from the existing tracking context (populated only under test; absent in production, so no capture occurs there).

**Queue name → meaning mapping (contract):**

| Today | Direction | New |
|---|---|---|
| `fps-outbound-submit` | bank→scheme | `payments-fps-command-submit.fifo` |
| `fps-inbound-returned` | bank→scheme | `payments-fps-command-return.fifo` |
| `fps-outbound-settled` | scheme→bank | `payments-fps-event-settled.fifo` |
| `fps-outbound-returned` | scheme→bank | `payments-fps-event-returned.fifo` |
| `fps-inbound-notify` | scheme→bank | `payments-fps-event-received.fifo` |
| `swift-outbound-submit` | bank→scheme | `payments-swift-command-submit.fifo` |
| `swift-outbound-settled` | scheme→bank | `payments-swift-event-settled.fifo` |
| `bacs-inbound-notify` | scheme→bank | `payments-bacs-event-received.fifo` |

Each gets a paired `…-dlq.fifo`. FIFO with `ContentBasedDeduplication=false` and explicit `messageDeduplicationId`; `messageGroupId` is the `paymentId` for outbound and the `endToEndId` for inbound.

**Producers (`payments-service`).** The three interfaces and their `InMemory*` and `Sqs*` implementations collapse to single concrete producers over `SqsTemplate` + catalog, encapsulating message construction and the group/dedup IDs. Sends are synchronous.

**Consumers (`payments-service`).** Thin `@SqsListener` adapters deserialize and delegate to the existing consumer/processor beans (`FpsOutboundSettledConsumer`, `FpsOutboundReturnedConsumer`, `SwiftOutboundSettledConsumer`, `InboundPaymentProcessor`), which retain all dedup and lifecycle logic. The `sqs_message_dedup` check stays, performed inside the listener transaction; its `MESSAGE_TYPE` constants are realigned to the new names.

**Stub counterparty (`malai-stub`).** Command-queue consumers become `@SqsListener`s; event producers use `SqsTemplate`; the inbound emitter and ambient (payroll) scheduler migrate accordingly, the scheduler keeping `@Scheduled`/`@SchedulerLock`. The direct `software.amazon.awssdk:sqs` dependency is removed.

**Provisioning lifecycle.** `malai.sqs.enabled` and all `@ConditionalOnProperty` guards removed; SQS always on. The per-queue-URL properties removed (names come from the catalog), keeping region, endpoint-override, credentials, and the new `queue-name-prefix`. The Localstack init script (`localstack-init-queues.sh`), its docker-compose `ready.d` hook, and the `MAL_SQS_*_QUEUE_URL` env vars are deleted. `malai-infra` adds no SQS Terraform.

**Test harness (`malai-acceptance-test-commons`).** An SQS interaction capturer modelled on `WireMockInteractionCapturer`, plus a pure `SqsClassifier` mapping a queue name to its scheme party (FPS / SWIFT / BACS) and a human description, plus the new scheme parties. Correlation via the `X-TestId` message attribute and the existing `CapturedInteractionHolder`. An `eventually {}` Awaitility helper. Localstack is added to `start-docker-containers-local.sh` and pointed at by acceptance tests; the per-JVM `queue-name-prefix` is set at boot.

**`WithPayments` rework (`payments-service` tests).** The `InMemory*` accessors (`theSubmittedFpsOutbounds`, `theLastSubmittedFpsOutbound`, `thePublishedInboundReturns`, the `…IsReset` resets) are replaced by real capture listeners on the command queues; the direct-invocation incoming helpers (`theStubScheduleSettlesPayment`, `theStubPublishesFpsInbound`, …) become real event producers onto the event queues. Assertions on resulting DB state poll via `eventually {}`.

**Documentation.** `malai-services/payments-service/CONTEXT.md`'s **Queue** entry is already updated (hyphenated-from-dotted naming, command/event perspective, `received`, app-startup provisioning). No ADRs — the naming doc and CONTEXT.md are deemed sufficient.

## Testing Decisions

**What makes a good test here.** Tests assert external behaviour over the real wire, never implementation internals: the *messages that appear on the queues*, the *resulting database state* (payment lifecycle, ledger, dedup), and the *interactions rendered on the Kensa diagram*. No test reaches into a producer's internal list or invokes a consumer's private method; the message goes onto a real queue and the observable effect is asserted (polling with `eventually {}` where the effect is asynchronous). Each test uses unique payment/end-to-end IDs and asserts on the message/state matching those IDs, so leftover messages from other tests are ignored.

**Modules tested.**
- **`payments-service` acceptance tests** — full round-trip against real Localstack queues, both directions: outbound commands captured off the command queues and asserted (and rendered on the diagram); inbound events produced onto the event queues and consumed by the bank's real `@SqsListener`s, with DB-state assertions polled.
- **`malai-stub` acceptance tests** — the stub consuming commands and producing events over real queues (the symmetric counterparty view).
- **Cross-stack E2E** (`malai-end-to-end-tests`) — the existing send/receive flows updated to the new names and app-side provisioning, run against the full stack over real SQS.

The pure helper modules (queue catalog, provisioner, `SqsClassifier`) are **not** given dedicated isolated unit tests; their correctness is covered transitively through the acceptance and E2E behaviour (wrong names break connectivity; a wrong classifier shows wrong diagram lanes), matching the project's behaviour-first posture.

**Prior art.** Follow `PaymentsAcceptanceTest` + `WithPayments` for the Kensa given/when/then style, full-entity DB assertions, and `responseEntity().containsProp(json { … noExtraProps() })` payload assertions. Model the SQS interaction capturer on `WireMockInteractionCapturer` / `MalaiHttpInteractionCapturer` and the `PartyLookupByUrl` / `InteractionDescriber` pattern. Reuse the `MalaiAcceptanceTestContext.boot(...)` once-per-JVM bootstrap to start/point at Localstack and provision the prefixed catalog. Mirror the existing E2E flow tests in `malai-end-to-end-tests`.

## Out of Scope

- **Terraform-managed queues in AWS** — deliberately app-provisioned instead; `malai-infra` adds no SQS resources.
- **Standard (non-FIFO) queues** — payment rails stay FIFO.
- **Removing the dedup table / intrinsic idempotency** (unique constraints + lifecycle guards) — the uniform `sqs_message_dedup` table is retained.
- **Per-queue `maxReceiveCount` tuning** — uniform `3`.
- **Dedicated unit tests for the catalog / provisioner / classifier** — covered transitively.
- **New payment rails, flows, or lifecycle changes** — this is the messaging layer only; payment behaviour, ledger, suspense topology, Confirmation of Payee (root ADR-0001), the payments bounded context (ADR-0002), and the audit chain are unchanged.
- **DLQ monitoring / alerting and redrive-to-source tooling** — an operational concern for later.
- **ADRs** — not produced for this work by decision.

## Further Notes

- The dependency swap is already underway in the working tree: `spring-cloud-aws-starter-sqs` + the `spring-cloud-aws-dependencies` BOM are in the root `pom.xml`, `malai-commons` already depends on the starter instead of `software.amazon.awssdk:sqs`, and `malai.sqs.enabled` has already been removed from the four property files and `StubCopControllerTest`.
- `docs/queues-naming-conventions.md` anticipates future `ledger`, `reconciliation`, and `notifications` domains; keeping the provisioner mechanism domain-agnostic in `malai-commons` (catalog in the domain module) is the deliberate accommodation for that growth.
- FIFO `PurgeQueue`'s 60-second cooldown is the specific reason test isolation uses a per-JVM prefix + unique IDs rather than purging between tests.
- The `queue-name-prefix` introduced for test isolation is intentionally also usable for environment separation in real AWS.
