# 07 — Cleanup + cross-stack E2E

Status: ready-for-agent

## Parent

[Real SQS messaging PRD](../PRD.md)

## What to build

Remove the migration scaffolding and prove the full stack over real SQS. Delete `malai.sqs.enabled` and every remaining `@ConditionalOnProperty` SQS guard, the per-queue-URL configuration properties, and the old awssdk-based `MalaiSqsConfiguration`. Delete `localstack-init-queues.sh`, its docker-compose `ready.d` init hook, and the `MAL_SQS_*_QUEUE_URL` environment variables — queues are now provisioned by the application alone. Align the dedup `MESSAGE_TYPE` constants to the new queue names. Update the cross-stack E2E send/receive flows to the new names and app-side provisioning and run them green.

## Acceptance criteria

- [x] No `malai.sqs.enabled`, `@ConditionalOnProperty` SQS guard, per-queue-URL property, or old `MalaiSqsConfiguration` remains; SQS is always on. (Deleted `malai-commons/.../sqs/MalaiSqsConfiguration.kt` — the awssdk `SqsClient` bean + `MalaiSqsConfigurationProperties`, both under `@ConditionalOnProperty(malai.sqs.enabled)` — and the dead `BacsInboundNotifyPoller.kt`, its only consumer. The remaining `@ConditionalOnProperty` usages in the tree — `AuditChainVerifierJob`, `StubAmbientActivityScheduler` — are audit/payroll, not SQS. A repo-wide grep finds `malai.sqs.enabled` only in `.scratch/`/PRD history.)
- [x] `localstack-init-queues.sh`, its docker-compose init hook, and the `MAL_SQS_*_QUEUE_URL` env vars are removed; the stack relies solely on app-side provisioning. (Deleted the script; removed the `ready.d` volume mount from `docker-compose.e2e.yml` and changed the localstack healthcheck off probing `fps-outbound-submit.fifo` to `sqs list-queues`; stripped all 8 `MAL_SQS_*_QUEUE_URL` vars from both service blocks; also dropped the manual `provision_queue` calls from `start-docker-containers-local.sh` so app-side provisioning is the single mechanism in local dev too. Verified at runtime: the e2e Localstack held exactly the 16 app-provisioned queues — 8 catalog + 8 `-dlq.fifo` — with no init hook present.)
- [x] The dedup `MESSAGE_TYPE` constants match the new queue names. (`FpsOutboundSettledConsumer`/`FpsOutboundReturnedConsumer`/`SwiftOutboundSettledConsumer` and `InboundPaymentProcessor.FPS_MESSAGE_TYPE`/`BACS_MESSAGE_TYPE` now reference `PaymentsQueues.{FPS_EVENT_SETTLED,FPS_EVENT_RETURNED,SWIFT_EVENT_SETTLED,FPS_EVENT_RECEIVED,BACS_EVENT_RECEIVED}` — equal to the catalog base names by construction. The DTO-level `messageType` discriminators in `malai-payments-messages` stay dotted, as noted in the PRD.)
- [x] The cross-stack E2E send/receive flows pass against the full stack over real SQS with app-provisioned queues. (`PaymentsTest` 7/7 green — FPS out to malai + cross-bank settle, SWIFT settle with FX, FPS + BACS inbound credit — plus `LocalstackTest` asserting the 16 app-provisioned names. Full stack: 8 containers healthy, payments-service + malai-stub self-provisioned the catalog at startup.)
- [x] `mvn install` is green and E2E is green. (`mvn install` BUILD SUCCESS across all 31 modules; `mvn test -pl malai-end-to-end-tests -De2e.skip=false` 19/19 green.)

## Blocked by

- 01 — Foundation + FPS happy-path round-trip on real SQS
- 02 — FPS outbound returned (sad path)
- 03 — FPS inbound received + return-to-sender
- 04 — SWIFT outbound round-trip
- 05 — BACS inbound received
- 06 — Stub counterparty migration
