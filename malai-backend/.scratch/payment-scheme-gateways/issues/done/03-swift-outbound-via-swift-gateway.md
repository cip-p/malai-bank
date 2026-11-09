# SWIFT outbound via `swift-gateway` (delayed settle)

Status: ready-for-agent

## Parent

`.scratch/payment-scheme-gateways/PRD.md`

## What to build

Route SWIFT outbound (international send) through a new `swift-gateway`, following the pattern established by `fps-gateway`, and remove the stub's SWIFT SQS path.

End-to-end behaviour: `payments-service` produces `payments-swift-command-submit` as today; `swift-gateway` consumes it, maps the canonical `SwiftOutboundSubmit` (IBAN/BIC, payment purpose, target amount/currency) onto a scheme-shaped SWIFT submit, and POSTs it to the external SWIFT scheme, expecting a `202` ack. SWIFT always settles in v1 (no return path), after a visible delay: the external scheme later calls the gateway's settlement webhook, and the gateway produces `payments-swift-event-settled`, so `payments-service` transitions `PENDING → SETTLED` with the delay the demo wants to show.

`swift-gateway` is a new `malai-services` module bundled into `malai-api` (registered in root `pom.xml`, no client-pair modules), dumb and stateless. `malai-stub` exposes the SWIFT submit/ack HTTP endpoint and a delayed settlement callback to the gateway, re-homing its SWIFT settlement behaviour behind HTTP; its SWIFT `@SqsListener` and `payments-swift-event-settled` producer are removed (atomic swap). `malai-scheme-contracts` grows the SWIFT submit/ack/settlement DTOs.

The stub still retains BACS SQS at the end of this slice. `payments-service` and `PaymentsQueues` are unchanged.

## Acceptance criteria

- [ ] `swift-gateway` module exists under `malai-services`, registered in root `pom.xml`, wired into `malai-api`, no client-pair modules.
- [ ] `swift-gateway` consumes `payments-swift-command-submit`, maps to a scheme-shaped SWIFT submit (IBAN/BIC, payment purpose, target amount/currency), and POSTs it expecting a `202` ack.
- [ ] On the scheme's delayed settlement callback, `swift-gateway` produces `payments-swift-event-settled` (FIFO group `paymentId`); there is no return path.
- [ ] An international payment settles `PENDING → SETTLED` after the visible delay, via the gateway path.
- [ ] `malai-stub` exposes the SWIFT submit/ack endpoint and fires a delayed settlement callback to the gateway; its SWIFT `@SqsListener` and settled producer are removed.
- [ ] `malai-scheme-contracts` carries the SWIFT DTOs.
- [ ] `payments-service` and `PaymentsQueues` unchanged.
- [ ] `swift-gateway` acceptance tests assert: feed `payments-swift-command-submit` → asserted HTTP submit (WireMock) → POST settlement callback → produced `payments-swift-event-settled`. Rendered on the Kensa diagram.
- [ ] `malai-stub` acceptance tests cover the SWIFT submit → delayed-settle callback path over HTTP.
- [ ] Cross-stack e2e SWIFT outbound traverses `swift-gateway` with the real `malai-stub`, and passes.
- [ ] `mvn install` is green.

## Blocked by

- `.scratch/payment-scheme-gateways/issues/02-fps-inbound-and-return-to-sender.md`
