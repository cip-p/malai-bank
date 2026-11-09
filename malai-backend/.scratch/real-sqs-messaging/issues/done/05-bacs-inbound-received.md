# 05 — BACS inbound received

Status: ready-for-agent

## Parent

[Real SQS messaging PRD](../PRD.md)

## What to build

Wire the **BACS Direct Credit** inbound received path. A received event on the BACS received (`event-received`) queue is consumed by an `@SqsListener` that delegates to the existing inbound processor on the BACS Direct Credit rail, crediting the beneficiary. Add the BACS scheme party/lane to the `SqsClassifier`. A payments acceptance test produces the real event and polls the credit.

## Acceptance criteria

- [x] A BACS received event credits the beneficiary and records the inbound payment on the BACS Direct Credit rail. `BacsInboundReceivedListener` (@SqsListener on `payments-bacs-event-received.fifo`) delegates to `InboundPaymentProcessor.process(BacsInboundNotify)` → `Rail.BACS_DIRECT_CREDIT`; verified by `salaryCreditFromBacsCreditsBeneficiaryAndRecordsBacsDirectCreditPayment` (SETTLED payment, BACS inbound suspense posting, beneficiary credit).
- [x] Receive-side dedup is enforced inside the listener transaction. `InboundPaymentProcessor` checks/saves `sqs_message_dedup` (key `bacs.inbound.notify`) inside its `@Transactional`; verified by `duplicateBacsInboundNotifyIsDeduplicatedAndCreditsOnlyOnce` (single payment, single credit).
- [x] A payments acceptance test produces the real BACS received event and polls the credit with `eventually {}`, with the interaction rendered in the BACS scheme lane. `theStubPublishesBacsInbound` sends via `SqsTemplate` onto `BACS_EVENT_RECEIVED`, captures the interaction (`SqsClassifier` maps `-bacs-` → `MalaiParty.Bacs`), and polls the committed dedup row.
- [x] `mvn install` is green. All 31 modules SUCCESS over Postgres + Localstack; e2e skipped (`e2e.skip=true`, owned by issue 07).

Red-green: with the listener moved out of the source tree all 5 BACS tests went RED at the dedup-row poll (`expected:<true> but was:<false> within 15 seconds` — event on the queue, nothing consuming it); restoring the listener turned them GREEN (4 happy + 1 sad).

## Blocked by

- 01 — Foundation + FPS happy-path round-trip on real SQS
