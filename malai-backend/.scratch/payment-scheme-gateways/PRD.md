# PRD: Per-rail scheme Gateways — the bank's FPS / BACS / SWIFT connectors

Status: ready-for-agent

## Problem Statement

The `real-sqs-messaging` work made `payments-service` talk to the rails over real FIFO queues, with `malai-stub` sitting on the far end *as* the scheme — consuming `command-*`, deciding settle-vs-return, and producing `event-*`. That wiring works, but it embeds an architectural fib that a buyer's payments architect will catch: **the bank is talking SQS directly to "Pay.UK".** SQS is an internal AWS primitive; it never crosses a bank's trust boundary to reach a real scheme. Real banks reach FPS, BACS, and SWIFT through dedicated per-rail **gateways** (an FPS gateway, a Bacs submission service, a SWIFT connector) — internal messaging on one side, scheme-protocol HTTP on the other.

So the seam a buyer would actually swap — "here's where you wire in the real Pay.UK / SWIFTNet integration" — is currently in the wrong place (an SQS queue), and the thing on the other side of it (`malai-stub`) is shaped like an SQS counterparty rather than like an external scheme's API. The payments rail is "the heart and core" of the demo; this is exactly where the architecture has to read as one a real bank would run.

There is no code on the bank's side that owns scheme interaction as its own concern, and `malai-stub` mixes "simulate the scheme's decisions" with "be an SQS endpoint" — two responsibilities that belong on opposite sides of the bank's boundary.

## Solution

Insert a per-rail **Gateway** layer that owns the SQS↔HTTP bridge, and demote `malai-stub` to an HTTP-only impersonation of the external schemes. This consciously supersedes `payments-service` ADR-0001 (the scheme-is-`malai-stub`-over-SQS wiring); the "separate deployable you can replace with a real integration" rationale survives, but the SQS boundary moves inward to the Gateway and the external boundary becomes HTTP.

- **Three Gateways, bundled into `malai-api`.** `fps-gateway`, `bacs-gateway`, `swift-gateway` — each a module under `malai-services`, bundled into the `malai-api` JVM like every other service. Each consumes the `command-*` **Queue**s `payments-service` already produces, maps the canonical message onto a recognizably **scheme-shaped** HTTP request, submits it to the external scheme, and — on the scheme's asynchronous callback — produces the matching `event-*` Queue back to `payments-service`. For inbound, it receives the scheme's webhook and produces the `event-received` Queue.
- **Gateways are dumb, stateless translators.** They translate *shape* (canonical ⇄ scheme-shaped) but make no *decisions*. No settle-vs-return logic, no magic-account directory, no database. Correlation identifiers (`endToEndId`, `paymentId`) travel on the wire, so no durable state is needed. All simulation logic stays in `malai-stub`.
- **Async external contract.** Submit returns a synchronous `202` "accepted for processing" ack; the settled/returned outcome arrives later as a callback to the Gateway's webhook. This matches how real schemes behave and exercises the Gateway's inbound webhook surface.
- **`malai-stub` becomes HTTP-only.** It exposes each external scheme's HTTP API (submit/ack endpoints), fires settle/return and inbound callbacks back at the Gateways, and re-homes its existing decision logic (`decide()` — magic accounts, armed returns, CoP-override→return, inbound generation) behind those HTTP handlers. By the end of the migration it has **no SQS code or dependencies** — the Gateways own all queue traffic.
- **Confirmation of Payee is untouched.** CoP stays a synchronous `account-service → malai-stub` HTTP lookup; it is not a queued rail message, so it does not route through a Gateway. `malai-stub` presents one coherent FPS-scheme HTTP surface (CoP for `account-service`, rail submit/ack/callback for `fps-gateway`).
- **`payments-service` does not change.** The Gateways take over `malai-stub`'s exact SQS role, so `PaymentsQueues` is frozen and `payments-service`'s producers, consumers, lifecycle, ledger, and suspense topology are all unchanged. Its own tests already fake the far end with `SqsTemplate`, so they don't care that a Gateway now occupies that role.
- **Migrate rail-by-rail, FPS first.** Each rail is a vertical slice that builds its Gateway, adds the stub's HTTP endpoints, and removes that rail's old stub-SQS code — all e2e-tested. The stub transitionally mixes SQS (un-migrated rails) and HTTP (migrated rails) but never runs two paths for the same rail; the demo is green at every merge.

The buyer reading the architecture now sees the honest picture: internal FIFO queues inside the bank, a per-rail gateway that translates to scheme-shaped messages and speaks HTTP to the outside world, asynchronous scheme acks and callbacks, and a swap-in seam — the external HTTP scheme — exactly where a real Pay.UK / SWIFTNet integration would sit.

## User Stories

### Architecture & the gateway boundary

1. As a bank technologist evaluating the demo, I want the bank to reach each payment scheme through a dedicated per-rail gateway, so that the architecture matches the FPS gateway / Bacs submission service / SWIFT connector pattern I run in production.
2. As a bank technologist, I want the bank to talk HTTP (not SQS) to the external schemes, so that the integration seam is realistic — SQS never crosses my trust boundary to Pay.UK or SWIFTNet.
3. As a bank technologist, I want a single, obvious place to swap the stub for a real scheme integration, so that licensing this platform means replacing `malai-stub`'s HTTP scheme APIs without touching `payments-service` or the gateways' internal wiring.
4. As a backend engineer, I want internal messaging to stop at the gateway and external messaging to be HTTP beyond it, so that the responsibilities — internal transport vs scheme protocol — are cleanly separated.
5. As a backend engineer, I want the gateways bundled into `malai-api` like every other service, so that the deployment topology stays flat and no new ECS services are required.

### FPS outbound (the deep rail)

6. As a customer, I want a payment I send to settle once the FPS scheme confirms it, so that the money reliably reaches the recipient.
7. As a backend engineer, I want `fps-gateway` to consume `payments-fps-command-submit`, map it to a scheme-shaped FPS submit, and POST it to the external FPS scheme, so that a queued command becomes a real outbound scheme message.
8. As a backend engineer, I want the FPS submit to return a synchronous `202` ack, so that the gateway can distinguish "accepted for processing" from a submission-level failure.
9. As a backend engineer, I want `fps-gateway` to receive the scheme's settlement callback and produce `payments-fps-event-settled`, so that `payments-service` transitions the payment `PENDING → SETTLED` exactly as it does today.
10. As a backend engineer, I want `fps-gateway` to receive the scheme's return callback (with a return reason) and produce `payments-fps-event-returned`, so that `payments-service` transitions the payment `PENDING → RETURNED` with the reason intact.
11. As a backend engineer, I want `fps-gateway` to consume `payments-fps-command-return` and POST a return-to-sender to the external scheme, so that the bank-initiated return path still reaches the rail.
12. As a demonstrator, I want the CoP-override→return story preserved end-to-end, so that sending past a name-mismatch warning still produces a returned payment — the decision made by the stub, delivered via the gateway callback.

### FPS inbound

13. As a customer, I want money sent to me over FPS to arrive in my account, so that I can receive payments from other banks and Malai customers.
14. As a backend engineer, I want `fps-gateway` to expose an inbound webhook the external scheme calls, mapping the scheme-shaped inbound message to `payments-fps-event-received`, so that `payments-service` credits the beneficiary or returns the payment as it does today.
15. As a demonstrator, I want the stub's inbound-trigger demo endpoint and ambient (payroll) scheduler to drive inbound by calling the gateway webhook over HTTP, so that triggered and recurring inbound payments still arrive without the stub touching SQS.
16. As a backend engineer, I want an unknown-beneficiary inbound to still produce a return-to-sender, so that "garbled inbound" behaviour is unchanged by the new transport.

### SWIFT outbound (always-settles)

17. As a customer, I want an international payment I send to settle, so that overseas transfers complete in the demo.
18. As a backend engineer, I want `swift-gateway` to consume `payments-swift-command-submit`, map it to a scheme-shaped SWIFT submit (IBAN/BIC, payment purpose), and POST it to the external SWIFT scheme, so that international sends become outbound scheme messages.
19. As a backend engineer, I want `swift-gateway` to receive a delayed settlement callback and produce `payments-swift-event-settled`, so that SWIFT's "settles after a visible delay" demo behaviour is preserved, with no return path in v1.

### BACS inbound (inbound-only)

20. As a customer, I want recurring credits like salary to land in my account, so that the demo shows realistic inbound activity.
21. As a backend engineer, I want `bacs-gateway` to expose an inbound webhook the external BACS scheme calls, mapping it to `payments-bacs-event-received`, so that BACS Direct Credit inbound is credited via `BACS_INBOUND_SUSPENSE` exactly as today.
22. As a backend engineer, I want `bacs-gateway` to have no outbound submit path, so that the gateway honestly reflects that v1 supports BACS-DC inbound only.

### Scheme-shaped contracts

23. As a bank technologist, I want the messages the gateway exchanges with the scheme to look like real scheme messages (debtor/creditor, pacs-like fields, reason codes), so that the external API reads as a credible Pay.UK / SWIFT integration.
24. As a backend engineer, I want the scheme HTTP contracts defined once in a shared module both the gateways and the stub depend on, so that the two sides of the boundary cannot drift apart.
25. As a backend engineer, I want the internal canonical contracts (`malai-payments-messages`) kept separate from the external scheme contracts (`malai-scheme-contracts`), so that "inside the bank" and "the scheme's API" stay visually distinct.

### `malai-stub` as the external scheme

26. As a backend engineer, I want `malai-stub` to expose each scheme's HTTP API (submit/ack) and an outbound callback client that calls the gateway webhooks, so that the stub behaves like the outside world over HTTP.
27. As a backend engineer, I want the stub's settle-vs-return decision logic (magic accounts, armed returns, CoP-override→return) re-homed behind the HTTP submit handler, so that all simulation stays in the stub and the gateways stay dumb.
28. As a demonstrator, I want the stub's `arm-return` and magic-account demo controls to keep working over HTTP, so that the demo's failure-path storytelling is unchanged.
29. As an operator, I want `malai-stub` to no longer connect to SQS by the end of the migration, so that it drops Localstack and the SQS dependency and is a pure HTTP impersonation of the schemes.

### Untouched guarantees

30. As a backend engineer, I want `PaymentsQueues` and all of `payments-service` (lifecycle, ledger, suspense, audit) left unchanged, so that this is a transport/boundary change only and existing payment guarantees hold.
31. As a backend engineer, I want Confirmation of Payee left as a direct `account-service → malai-stub` HTTP lookup, so that a working synchronous flow is not disturbed by the rail-message gateways.

### Migration safety

32. As a demonstrator, I want the payments demo to stay green at every merge, so that I can show the product at any point during the migration.
33. As a backend engineer, I want each rail migrated as one vertical slice (gateway + stub HTTP + remove that rail's stub-SQS), so that no rail ever runs two paths at once and the blast radius per change is one rail.
34. As a backend engineer, I want FPS migrated first, so that the only rail with the full settle/return/inbound surface proves the pattern before the shallower BACS and SWIFT follow.
35. As a backend engineer, I want the final slice to remove the stub's last SQS dependency, so that "the stub no longer connects to SQS" is verifiably complete.

### Testing & diagram storytelling

36. As a test author, I want each gateway's acceptance tests to assert both SQS edges and both HTTP edges of a flow, so that "test all outgoing and incoming messages" is satisfied at the gateway boundary.
37. As a test author, I want the external scheme stubbed by WireMock in gateway acceptance tests and by `malai-stub` in cross-stack e2e, so that the two-level stubbing matches the house pattern.
38. As a developer reading a gateway test report, I want the SQS commands/events and the scheme HTTP calls rendered together on the Kensa sequence diagram, so that the bank's full conversation with the scheme — queue in, HTTP out, callback in, queue out — is visible.

## Implementation Decisions

**`malai-scheme-contracts` (new shared module, deep, under `malai-clients`).** The scheme-shaped HTTP contract DTOs — submit request, `202` ack, settlement/return callback bodies, inbound webhook bodies, per rail — defined once and depended on by both the gateways and `malai-stub`. Kept separate from `malai-payments-messages` (which remains the internal canonical SQS contracts) so internal-vs-external stays distinct. Pure data classes, no I/O. The stub depends on this neutral contract module, never on gateway code (the dependency direction stays "external world → neutral contract ← bank's connector").

**Shape translator (deep module, one per gateway).** A pure function mapping the canonical message (`FpsOutboundSubmit`, `SwiftOutboundSubmit`, …) to its scheme-shaped request, and the scheme-shaped callback back to the canonical event (`FpsOutboundSettled`/`Returned`, `FpsInboundNotify`, …), including return-reason-code translation. No I/O, rarely changes — the one piece of each gateway that holds real logic. The listeners, webhook controllers, and HTTP client are thin adapters around it.

**`fps-gateway` (new `malai-services` module, bundled into `malai-api`).** `@SqsListener`s on `payments-fps-command-submit` and `payments-fps-command-return`; an outbound HTTP client to the external FPS scheme (submit, expecting `202`); webhook controllers receiving settlement, return, and inbound callbacks; `SqsTemplate` producers for `payments-fps-event-settled`, `-returned`, and `-received`. Stateless.

**`swift-gateway` (new module).** `@SqsListener` on `payments-swift-command-submit`; outbound HTTP submit; a settlement-callback webhook; `SqsTemplate` producer for `payments-swift-event-settled`. No return path, no inbound.

**`bacs-gateway` (new module).** Inbound-only: a webhook receiving BACS Direct Credit inbound from the external scheme; `SqsTemplate` producer for `payments-bacs-event-received`. No outbound submit path.

**External HTTP contract (async).** Submit → synchronous `202` ack. Outcome (settled/returned) and inbound arrival → asynchronous callback to a gateway webhook, carrying the correlation IDs. The gateway holds no in-flight state; it maps the callback onto the canonical event and produces it. FIFO `messageGroupId`/`messageDeduplicationId` on the produced events follow the existing convention (`paymentId` for outbound, `endToEndId` for inbound).

**`malai-stub` rework.** Per rail: replace the `@SqsListener` command consumers and `SqsTemplate` event producers with (a) HTTP controllers exposing the scheme's submit/ack endpoints, and (b) an outbound callback HTTP client that calls the gateway webhooks. The existing `decide()` logic (magic-account directory, armed return, CoP-override→return) moves behind the submit handler and fires the appropriate callback. The inbound demo endpoints and the ambient (payroll) scheduler call the gateway inbound webhook over HTTP instead of producing SQS. The `malai-sqs` dependency, Localstack wiring, and the SQS interaction recorder are removed once the last rail is migrated. The decision logic, magic-account directory, and ambient scheduler are retained behaviourally.

**`malai-api` & root `pom.xml`.** Add `fps-gateway`, `swift-gateway`, `bacs-gateway` as dependencies of `malai-api` and import each gateway's `@Configuration`. Register all three in the root `pom.xml` `<modules>` and `<dependencyManagement>`; register `malai-scheme-contracts` likewise. The gateways need **no** `-client` / `-client-test-support` pair modules — the gateway↔scheme boundary is the external seam, not an internal malai service-to-service call.

**Queue ownership & provisioning.** `PaymentsQueues` is frozen. The gateways become the bank-side consumer of `command-*` and producer of `event-*`; they (being in `malai-api`) provision the payments queues via the existing domain-agnostic `QueueCatalog` / provisioner mechanism in `malai-commons`. `malai-stub` no longer participates in provisioning.

**`payments-service`.** No change. Producers, consumers (`FpsOutboundSettledConsumer`, `FpsOutboundReturnedConsumer`, `SwiftOutboundSettledConsumer`, `InboundPaymentProcessor`), lifecycle, ledger, suspense topology, dedup, and audit chain are all untouched.

**`malai-infra` (recorded for the infra repo).** `malai-stub`'s ECS task loses its SQS permissions by the end of the migration; `malai-api` is unchanged (already has SQS). No new ECS service.

**Supersession.** `payments-service` ADR-0002 records this design and supersedes ADR-0001; ADR-0001 is marked superseded with a pointer. `CONTEXT.md` (the `Gateway` term, plus revised `Scheme` / `Stub` / `Queue`) and `CONTEXT-MAP.md` (three new gateway contexts; updated `payments-service` and `malai-stub` rows) are already updated.

## Testing Decisions

**What makes a good test here.** Tests assert external behaviour at the gateway's two boundaries — never its internals. The observable facts are: the *scheme-shaped HTTP request* the gateway sends (asserted against WireMock), and the *SQS event* it produces (asserted via a capture listener), each correlated by `paymentId`/`endToEndId` so concurrent tests don't cross-talk. A test feeds one edge (produce a `command-*` message, or POST a callback/inbound webhook) and asserts the opposite edge, polling with `eventually {}` for the asynchronous hop. No test reaches into a translator's internals or invokes a controller method directly.

**Modules tested.**
- **`fps-gateway` / `swift-gateway` / `bacs-gateway` acceptance tests** (Kensa BDD) — the SQS↔HTTP round-trip per rail. Outbound: feed `command-*` (`SqsTemplate`) → assert the scheme-shaped HTTP submit (WireMock) → POST the gateway's callback webhook → assert `event-*` (capture listener). Inbound: POST the inbound webhook → assert `event-received`. WireMock is the external scheme at this tier.
- **`malai-stub` acceptance tests** — reworked from SQS-in/SQS-out to HTTP-in/HTTP-out: the stub receives a scheme submit, runs `decide()`, and fires the matching callback (settle/return); the inbound endpoints/ambient scheduler call the gateway webhook. The symmetric "external scheme" view.
- **Cross-stack e2e** (`malai-end-to-end-tests`) — the existing send/receive flows updated so each rail traverses its gateway with the real `malai-stub` as the external HTTP scheme.

**Not tested in isolation.** The per-gateway shape translators and the `malai-scheme-contracts` DTOs get **no** dedicated unit tests; their correctness is covered transitively through the gateway acceptance tests (a wrong field mapping breaks the round-trip assertion). This matches the `real-sqs-messaging` PRD's deliberate stance on the catalog / provisioner / classifier and the project's behaviour-first posture.

**Prior art.** Model gateway acceptance tests on `PaymentsAcceptanceTest` + `WithPayments` (Kensa given/when/then, full-entity DB assertions, `responseEntity().containsProp(json { … noExtraProps() })`), composed with the SQS harness those tests already use — `SqsTemplate` to feed queues, the `FpsCommandCaptureListener` → `CapturedFpsCommands` → `StateCollector` capture pattern to assert produced messages, `TestId`-header correlation, per-JVM `queue-name-prefix` isolation, and `eventually {}`. Reuse `SqsInteractionCapturer` so every queue message renders on the Kensa diagram alongside the WireMock HTTP interactions. The stub's reworked HTTP tests follow the existing `StubCopControllerTest` MockMvc style, replacing `StubSchemeAcceptanceTest`'s SQS-round-trip tests.

## Out of Scope

- **Changes to `payments-service`** — lifecycle, ledger, suspense topology, dedup, audit chain, and `PaymentsQueues` are frozen; this is a transport/boundary change only.
- **Confirmation of Payee routing** — CoP stays a direct `account-service → malai-stub` HTTP call, not routed through a gateway.
- **Separate deployables for the gateways** — they are bundled into `malai-api`; no new ECS service.
- **`-client` / `-client-test-support` pair modules for the gateways** — the gateway↔scheme boundary is external, not an internal malai call.
- **Full ISO 20022 (pacs.008 / pacs.002 / camt) XML** — the scheme contracts are recognizably scheme-shaped but simplified JSON.
- **A gateway database / durable in-flight state** — gateways are stateless; correlation IDs travel on the wire.
- **Outbound BACS and SWIFT returns** — BACS is inbound-only and SWIFT always-settles in v1.
- **New payment rails, flows, or lifecycle states** — no CHAPS, no SEPA, no recall path.
- **Isolated unit tests for translators / scheme-contract DTOs** — covered transitively.
- **`malai-infra` Terraform changes** — recorded here for the infra repo; actioned in `malai-infra`, not this work.

## Further Notes

- This supersedes `payments-service` ADR-0001. The "why a separate deployable rather than in-process beans" rationale still holds; what changed is where the SQS boundary sits — it now stops at the gateway inside `malai-api`, and `malai-stub` speaks HTTP.
- During migration `malai-stub` legitimately runs a mixed transport: SQS for un-migrated rails, HTTP for migrated ones. This is expected and temporary; it never means two live paths for the *same* rail.
- The async ack-plus-callback contract is deliberate: a synchronous outcome on the submit response would be simpler but unrealistic, and it would skip the gateway's inbound webhook surface that inbound payments need regardless.
- `docs/queues-naming-conventions.md` and `PaymentsQueues` are unchanged — the queues keep their names; only their bank-side counterparty changes from `malai-stub` to the gateways.
- Natural slice order: (1) `malai-scheme-contracts` + the FPS contracts and `fps-gateway` outbound settle/return; (2) FPS inbound + return-to-sender, retiring the stub's FPS SQS code; (3) `swift-gateway`; (4) `bacs-gateway`, retiring the stub's last SQS dependency. `/to-issues` can break these into tickets.
