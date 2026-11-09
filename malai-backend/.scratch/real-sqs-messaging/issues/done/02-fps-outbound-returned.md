# 02 — FPS outbound returned (sad path)

Status: ready-for-agent

## Parent

[Real SQS messaging PRD](../PRD.md)

## What to build

Wire the **FPS outbound returned** event end-to-end. A returned event placed on the FPS returned (`event-returned`) queue is consumed by an `@SqsListener` that delegates to the existing returned consumer, which credits the customer back and moves the payment PENDING→RETURNED carrying its return reason. Rework the FPS-return test helper to produce the real event onto the queue (the test playing the scheme), and assert the lifecycle and ledger reversal via polling. The returned interaction renders on the Kensa diagram in the FPS lane.

## Acceptance criteria

- [x] A returned event on the FPS returned queue is consumed by an `@SqsListener` and moves the payment PENDING→RETURNED with the return reason recorded. — `FpsOutboundReturnedListener` on `payments-fps-event-returned.fifo` delegates to the existing `FpsOutboundReturnedConsumer` → `PaymentLifecycleService.transitionToReturned(paymentId, reason)`.
- [x] The customer is credited back and the ledger reflects the reversal. — covered by the reversal-pair + debtor-refund ledger assertions in the returned tests.
- [x] Receive-side dedup is enforced inside the listener transaction (a redelivered returned event does not double-reverse). — `FpsOutboundReturnedConsumer.process` is `@Transactional`; `duplicateReturnedMessageIsDeduplicatedNoSecondReversal` now drives two real events through the listener and asserts one reversal / one `PAYMENT_RETURNED`.
- [x] A payments acceptance test produces the real returned event and polls the RETURNED lifecycle and credit-back with `eventually {}`. — `theStubScheduleReturnsPayment` reworked to `SqsTemplate.send` onto `FPS_EVENT_RETURNED` + `eventually { state == RETURNED }`, mirroring `theStubScheduleSettlesPayment`. All `FpsOutboundReturnReasonsControllerTest` + `FpsOutboundExternalPaymentsControllerTest` returned cases now exercise the real wire.
- [x] The returned interaction renders on the Kensa sequence diagram in the FPS scheme lane. — `SqsInteractionCapturer.captureEvent(..., FPS_EVENT_RETURNED, ...)`; `SqsClassifier` maps `-fps-` to the FPS party lane.
- [x] `mvn install` is green. — green across the reactor (e2e skipped via `e2e.skip=true`, owned by issue 07).

## Blocked by

- 01 — Foundation + FPS happy-path round-trip on real SQS
