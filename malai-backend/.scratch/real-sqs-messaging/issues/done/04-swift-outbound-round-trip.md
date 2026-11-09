# 04 — SWIFT outbound round-trip

Status: ready-for-agent

## Parent

[Real SQS messaging PRD](../PRD.md)

## What to build

Migrate the **SWIFT** outbound submit→settled round-trip onto real queues. A concrete SWIFT producer sends the submit command via `SqsTemplate`; an `@SqsListener` consumes the settled event and delegates to the existing SWIFT settled consumer. Delete `InMemorySwiftScheme` and the `SwiftScheme` interface. Add the SWIFT scheme party/lane to the `SqsClassifier`. A payments acceptance test proves the round-trip with the captured submit command and the produced settled event.

## Acceptance criteria

- [x] An international (SWIFT) payment publishes a submit command to the SWIFT command queue via `SqsTemplate`. — new `SwiftCommandProducer` sends `SwiftOutboundSubmit` to `payments-swift-command-submit.fifo`; `PaymentsService` injects it in place of the deleted `SwiftScheme`.
- [x] A settled event on the SWIFT settled queue is consumed by an `@SqsListener` and settles the payment. — new `SwiftOutboundSettledListener` on `payments-swift-event-settled.fifo` delegates to the existing `SwiftOutboundSettledConsumer`.
- [x] `InMemorySwiftScheme` and the `SwiftScheme` interface are deleted. — also deleted the dead `SqsSwiftScheme` (awssdk) and `SwiftOutboundSettledPoller`.
- [x] A payments acceptance test proves the round-trip against real Localstack queues (captured submit, produced settled, polled SETTLED), with interactions rendered in the SWIFT scheme lane. — `SwiftOutboundPaymentsControllerTest` (happy 4 incl. submit-capture + settled + dedup, sad 1) green over real Localstack; SWIFT party/lane already present in `SqsClassifier`.
- [x] `mvn install` is green. — full build green across all modules (exit 0); malai-api boots with the new listener. e2e skipped (e2e.skip=true, owned by issue 07).

## Blocked by

- 01 — Foundation + FPS happy-path round-trip on real SQS
