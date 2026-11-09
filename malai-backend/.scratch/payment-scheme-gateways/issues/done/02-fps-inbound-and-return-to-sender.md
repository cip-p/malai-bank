# FPS inbound + return-to-sender

Status: ready-for-agent

## Parent

`.scratch/payment-scheme-gateways/PRD.md`

## What to build

Complete FPS by moving the *inbound* and *return-to-sender* paths onto `fps-gateway`, removing the stub's last FPS SQS code.

Inbound: `fps-gateway` exposes an HTTP webhook the external FPS scheme calls when money arrives. The gateway maps the scheme-shaped inbound message onto the canonical `FpsInboundNotify` and produces `payments-fps-event-received`, so `payments-service` credits the beneficiary via `FPS_INBOUND_SUSPENSE` or, for an unknown beneficiary, produces a return — both unchanged from today. `malai-stub` drives inbound by calling the gateway webhook over HTTP: its inbound demo endpoint (`/demo/inbound/fps`) and its ambient (payroll) scheduler now POST to the gateway instead of producing onto SQS. The ambient scheduler keeps its `@Scheduled` / `@SchedulerLock`.

Return-to-sender: `fps-gateway` consumes `payments-fps-command-return` and POSTs a scheme-shaped return-to-sender to the external FPS scheme over HTTP; the stub records it via its HTTP handler.

Removed in this slice: the stub's `payments-fps-event-received` `SqsTemplate` producer (inbound emitter) and its `payments-fps-command-return` `@SqsListener`. After this slice the stub has **no FPS SQS code** — only SWIFT and BACS SQS remain. `malai-scheme-contracts` grows the FPS inbound + return-to-sender DTOs.

`payments-service` and `PaymentsQueues` remain unchanged.

## Acceptance criteria

- [ ] `fps-gateway` exposes an inbound webhook that maps the scheme-shaped inbound message to `FpsInboundNotify` and produces `payments-fps-event-received` (FIFO group `endToEndId`).
- [ ] A triggered inbound to a known beneficiary credits the account (`PENDING → SETTLED` via `FPS_INBOUND_SUSPENSE`); an unknown beneficiary produces a return-to-sender — both unchanged from current behaviour.
- [ ] `fps-gateway` consumes `payments-fps-command-return` and POSTs a return-to-sender to the external FPS scheme over HTTP.
- [ ] `malai-stub`'s `/demo/inbound/fps` endpoint and ambient payroll scheduler call the gateway inbound webhook over HTTP; the ambient scheduler retains `@Scheduled` / `@SchedulerLock`.
- [ ] The stub's FPS inbound emitter (`payments-fps-event-received` producer) and `payments-fps-command-return` listener are removed; no FPS SQS code remains in the stub.
- [ ] `malai-scheme-contracts` carries the FPS inbound + return-to-sender DTOs.
- [ ] `payments-service` and `PaymentsQueues` unchanged.
- [ ] `fps-gateway` acceptance tests assert: POST inbound webhook → produced `payments-fps-event-received`; and feed `payments-fps-command-return` → asserted HTTP return-to-sender (WireMock). Rendered on the Kensa diagram.
- [ ] `malai-stub` acceptance tests cover the inbound-trigger and ambient paths calling the gateway webhook over HTTP.
- [ ] Cross-stack e2e FPS inbound (credited + unknown-beneficiary return) traverses `fps-gateway` with the real `malai-stub`, and passes.
- [ ] `mvn install` is green.

## Blocked by

- `.scratch/payment-scheme-gateways/issues/01-fps-outbound-via-fps-gateway.md`
