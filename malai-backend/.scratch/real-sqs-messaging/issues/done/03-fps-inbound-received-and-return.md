# 03 — FPS inbound received + return-to-sender

Status: ready-for-agent

## Parent

[Real SQS messaging PRD](../PRD.md)

## What to build

Wire the **FPS inbound** path end-to-end in both outcomes. A received event on the FPS received (`event-received`) queue is consumed by an `@SqsListener` that delegates to the existing inbound processor:

- **Known beneficiary** — credit the customer, minting an inbound payment.
- **Unknown beneficiary** — publish a return-to-sender **command** onto the FPS `command-return` queue (reason `INVALID_ACCOUNT`) via a concrete producer.

Delete `InMemoryInboundReturnPublisher` and the `InboundReturnPublisher` interface; the producer becomes concrete over `SqsTemplate`. Rework the inbound test helpers so the test produces the real received event and captures the real outgoing return command. Both flows are proven against real queues, with the outgoing return rendered in the FPS lane.

## Acceptance criteria

- [x] A received event for a known beneficiary credits the customer and records the inbound payment. — `FpsInboundReceivedListener` (`@SqsListener` on `payments-fps-event-received.fifo`) delegates to `InboundPaymentProcessor`; `FpsInboundPaymentsControllerTest.knownBeneficiaryReceivesCreditAndAuditEventIsRecorded` green over real Localstack.
- [x] Receive-side dedup inside the listener transaction prevents a redelivered received event from double-crediting (asserted). — `duplicateInboundNotifyIsDeduplicatedAndCreditsOnlyOnce` sends two received events (same `messageGroupId=endToEndId`, distinct `messageDeduplicationId`); the `@Transactional` processor dedups via `sqs_message_dedup`; one payment, one credit. Logs show `fps.inbound.notify deduplicated` for the second.
- [x] A received event for an unknown beneficiary publishes a return command (reason `INVALID_ACCOUNT`) onto the FPS `command-return` queue. — new concrete `FpsReturnCommandProducer` sends `FpsInboundReturned` to `payments-fps-command-return.fifo`; `unknownBeneficiaryAutoReturnsAndDoesNotWriteAPaymentRow` (happy) + sad variant green.
- [x] `InMemoryInboundReturnPublisher` and the `InboundReturnPublisher` interface are deleted; the producer is concrete over `SqsTemplate`. — also deleted `SqsInboundReturnPublisher` (old awssdk impl) and the dead `FpsInboundNotifyPoller`; `InboundPaymentProcessor` now injects `FpsReturnCommandProducer`.
- [x] Acceptance tests cover both outcomes against real Localstack queues: known-beneficiary credit (polled with `eventually {}`) and unknown-beneficiary outgoing return (captured + rendered in the FPS scheme lane). — `theStubPublishesFpsInbound` produces the real received event and polls the committed `sqs_message_dedup` row with `eventually {}`; `FpsReturnCommandCaptureListener` captures the return off the command queue and renders it via `SqsInteractionCapturer.captureCommand` (FPS lane).
- [x] `mvn install` is green. — full multi-module build green (exit 0), e2e skipped (`e2e.skip=true`, owned by issue 07).

## Blocked by

- 01 — Foundation + FPS happy-path round-trip on real SQS
