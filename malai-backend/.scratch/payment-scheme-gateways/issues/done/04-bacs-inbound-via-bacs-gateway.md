# BACS inbound via `bacs-gateway` (+ retire stub's last SQS)

Status: ready-for-agent

## Parent

`.scratch/payment-scheme-gateways/PRD.md`

## What to build

The final slice: route BACS Direct Credit inbound through a new `bacs-gateway`, and remove the stub's last SQS path so `malai-stub` no longer connects to SQS at all.

End-to-end behaviour: `bacs-gateway` exposes an HTTP webhook the external BACS scheme calls when an inbound credit (e.g. salary) arrives. The gateway maps the scheme-shaped inbound message onto the canonical `BacsInboundNotify` and produces `payments-bacs-event-received`, so `payments-service` credits the beneficiary via `BACS_INBOUND_SUSPENSE` exactly as today. BACS is **inbound-only** in v1 — the gateway has no outbound submit path. `malai-stub` drives BACS inbound (its `/demo/inbound/bacs` endpoint and any ambient BACS activity) by calling the gateway webhook over HTTP instead of producing onto SQS; its `payments-bacs-event-received` producer is removed.

`bacs-gateway` is a new `malai-services` module bundled into `malai-api` (registered in root `pom.xml`, no client-pair modules), dumb and stateless. `malai-scheme-contracts` grows the BACS inbound DTOs.

Because this removes the stub's last SQS usage, this slice also drops `malai-sqs` and Localstack wiring from `malai-stub` and removes any now-dead SQS interaction-recording code — completing the PRD goal that the stub communicates only over HTTP.

`payments-service` and `PaymentsQueues` remain unchanged.

## Acceptance criteria

- [ ] `bacs-gateway` module exists under `malai-services`, registered in root `pom.xml`, wired into `malai-api`, no client-pair modules, with **no outbound submit path**.
- [ ] `bacs-gateway` exposes an inbound webhook that maps the scheme-shaped inbound message to `BacsInboundNotify` and produces `payments-bacs-event-received` (FIFO group `endToEndId`).
- [ ] A BACS inbound credit lands `PENDING → SETTLED` via `BACS_INBOUND_SUSPENSE`, unchanged from current behaviour.
- [ ] `malai-stub`'s BACS inbound endpoint / ambient activity calls the gateway webhook over HTTP; its `payments-bacs-event-received` producer is removed.
- [ ] `malai-stub` no longer depends on `malai-sqs`, no longer uses Localstack, and contains no SQS code; dead SQS interaction-recording code is removed.
- [ ] `malai-scheme-contracts` carries the BACS inbound DTOs.
- [ ] `payments-service` and `PaymentsQueues` unchanged.
- [ ] `bacs-gateway` acceptance tests assert: POST inbound webhook → produced `payments-bacs-event-received`. Rendered on the Kensa diagram.
- [ ] `malai-stub` acceptance tests cover the BACS inbound trigger calling the gateway webhook over HTTP.
- [ ] Cross-stack e2e BACS inbound traverses `bacs-gateway` with the real `malai-stub`, and passes.
- [ ] No payment behaviour, ledger posting, suspense topology, CoP, or audit-chain change across the whole feature.
- [ ] `mvn install` is green.

## Blocked by

- `.scratch/payment-scheme-gateways/issues/03-swift-outbound-via-swift-gateway.md`
